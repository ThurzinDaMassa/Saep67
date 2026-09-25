package br.saep.estoque;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class Store {
    private static final String URL = System.getenv().getOrDefault("SAEP_DB_URL", "jdbc:mariadb://localhost:3306/saep_db");
    private static final String DB_USER = System.getenv().getOrDefault("SAEP_DB_USER", "root");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("SAEP_DB_PASSWORD", "");

    private Connection connect() throws SQLException { return DriverManager.getConnection(URL, DB_USER, DB_PASSWORD); }

    public static final class User {
        public final long id;
        public final String name;
        User(long id, String name) { this.id = id; this.name = name; }
    }

    public static final class Profile {
        public final String name, login, role, jobTitle, bio;
        public final boolean hasPhoto, hasBanner;
        Profile(String name, String login, String role, String jobTitle, String bio, boolean hasPhoto, boolean hasBanner) {
            this.name = name; this.login = login; this.role = role; this.jobTitle = jobTitle; this.bio = bio;
            this.hasPhoto = hasPhoto; this.hasBanner = hasBanner;
        }
    }

    public static final class ProfileImage {
        public final byte[] bytes;
        public final String mime;
        ProfileImage(byte[] bytes, String mime) { this.bytes = bytes; this.mime = mime; }
    }

    private void ensureProfileTable(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS perfis (" +
                "usuario_id BIGINT PRIMARY KEY, cargo VARCHAR(80) NOT NULL DEFAULT '', bio VARCHAR(500) NOT NULL DEFAULT '', " +
                "foto MEDIUMBLOB NULL, foto_mime VARCHAR(20) NULL, banner MEDIUMBLOB NULL, banner_mime VARCHAR(20) NULL, " +
                "CONSTRAINT fk_perfil_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE)");
        }
    }

    public Profile profile(long userId) throws SQLException {
        try (Connection c = connect()) {
            ensureProfileTable(c);
            try (PreparedStatement p = c.prepareStatement("SELECT u.nome, u.login, u.perfil, " +
                    "COALESCE(pf.cargo, ''), COALESCE(pf.bio, ''), pf.foto_mime, pf.banner_mime " +
                    "FROM usuarios u LEFT JOIN perfis pf ON pf.usuario_id = u.id WHERE u.id = ? AND u.ativo = TRUE")) {
                p.setLong(1, userId);
                try (ResultSet r = p.executeQuery()) {
                    if (!r.next()) throw new IllegalArgumentException("Usuário não encontrado.");
                    return new Profile(r.getString(1), r.getString(2), r.getString(3), r.getString(4), r.getString(5),
                        r.getString(6) != null, r.getString(7) != null);
                }
            }
        }
    }

    public User updateProfile(long userId, String name, String login, String jobTitle, String bio) throws SQLException {
        if (name == null || name.trim().isEmpty() || name.trim().length() > 100)
            throw new IllegalArgumentException("Nome deve ter entre 1 e 100 caracteres.");
        if (login == null || !login.trim().matches("[A-Za-z0-9._-]{3,50}"))
            throw new IllegalArgumentException("Usuário deve ter de 3 a 50 caracteres: letras, números, ponto, hífen ou sublinhado.");
        if (jobTitle == null || jobTitle.trim().length() > 80)
            throw new IllegalArgumentException("Função deve ter até 80 caracteres.");
        if (bio == null || bio.trim().length() > 500)
            throw new IllegalArgumentException("Bio deve ter até 500 caracteres.");
        try (Connection c = connect()) {
            ensureProfileTable(c);
            c.setAutoCommit(false);
            try {
                try (PreparedStatement p = c.prepareStatement("UPDATE usuarios SET nome = ?, login = ? WHERE id = ? AND ativo = TRUE")) {
                    p.setString(1, name.trim()); p.setString(2, login.trim()); p.setLong(3, userId);
                    if (p.executeUpdate() == 0) throw new IllegalArgumentException("Usuário não encontrado.");
                }
                try (PreparedStatement p = c.prepareStatement("INSERT INTO perfis (usuario_id, cargo, bio) VALUES (?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE cargo = VALUES(cargo), bio = VALUES(bio)")) {
                    p.setLong(1, userId); p.setString(2, jobTitle.trim()); p.setString(3, bio.trim()); p.executeUpdate();
                }
                c.commit();
                return new User(userId, name.trim());
            } catch (Exception e) {
                c.rollback();
                if (e instanceof SQLException) throw (SQLException)e;
                throw e;
            }
        }
    }

    public void changePassword(long userId, char[] current, char[] next) throws Exception {
        if (next.length < 8 || next.length > 128) throw new IllegalArgumentException("A nova senha deve ter entre 8 e 128 caracteres.");
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            try {
                String stored;
                try (PreparedStatement p = c.prepareStatement("SELECT senha_hash FROM usuarios WHERE id = ? AND ativo = TRUE FOR UPDATE")) {
                    p.setLong(1, userId);
                    try (ResultSet r = p.executeQuery()) {
                        if (!r.next()) throw new IllegalArgumentException("Usuário não encontrado.");
                        stored = r.getString(1);
                    }
                }
                if (!verifyPassword(current, stored)) throw new IllegalArgumentException("Senha atual incorreta.");
                if (Arrays.equals(current, next)) throw new IllegalArgumentException("Escolha uma senha diferente da atual.");
                byte[] salt = new byte[16]; new SecureRandom().nextBytes(salt);
                PBEKeySpec spec = new PBEKeySpec(next, salt, 120000, 256);
                byte[] hash;
                try { hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
                finally { spec.clearPassword(); }
                try (PreparedStatement p = c.prepareStatement("UPDATE usuarios SET senha_hash = ? WHERE id = ?")) {
                    p.setString(1, toHex(salt) + ":" + toHex(hash)); p.setLong(2, userId); p.executeUpdate();
                }
                Arrays.fill(hash, (byte)0);
                c.commit();
            } catch (Exception e) { c.rollback(); throw e; }
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) result.append(Character.forDigit((b >>> 4) & 15, 16)).append(Character.forDigit(b & 15, 16));
        return result.toString();
    }

    public void saveImage(long userId, String kind, byte[] bytes, String mime) throws SQLException {
        if (!kind.equals("foto") && !kind.equals("banner")) throw new IllegalArgumentException("Imagem inválida.");
        try (Connection c = connect()) {
            ensureProfileTable(c);
            try (PreparedStatement p = c.prepareStatement("INSERT INTO perfis (usuario_id, " + kind + ", " + kind + "_mime) VALUES (?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE " + kind + " = VALUES(" + kind + "), " + kind + "_mime = VALUES(" + kind + "_mime)")) {
                p.setLong(1, userId); p.setBytes(2, bytes); p.setString(3, mime); p.executeUpdate();
            }
        }
    }

    public void removeImage(long userId, String kind) throws SQLException {
        if (!kind.equals("foto") && !kind.equals("banner")) throw new IllegalArgumentException("Imagem inválida.");
        try (Connection c = connect()) {
            ensureProfileTable(c);
            try (PreparedStatement p = c.prepareStatement("UPDATE perfis SET " + kind + " = NULL, " + kind + "_mime = NULL WHERE usuario_id = ?")) {
                p.setLong(1, userId); p.executeUpdate();
            }
        }
    }

    public ProfileImage image(long userId, String kind) throws SQLException {
        if (!kind.equals("foto") && !kind.equals("banner")) throw new IllegalArgumentException("Imagem inválida.");
        try (Connection c = connect()) {
            ensureProfileTable(c);
            try (PreparedStatement p = c.prepareStatement("SELECT " + kind + ", " + kind + "_mime FROM perfis WHERE usuario_id = ?")) {
                p.setLong(1, userId);
                try (ResultSet r = p.executeQuery()) {
                    if (!r.next() || r.getBytes(1) == null) return null;
                    return new ProfileImage(r.getBytes(1), r.getString(2));
                }
            }
        }
    }

    public static final class Product {
        public final long id;
        public final String name, description;
        public final int current, minimum;
        Product(long id, String name, String description, int current, int minimum) {
            this.id = id; this.name = name; this.description = description; this.current = current; this.minimum = minimum;
        }
    }

    public static final class Movement {
        public final long id;
        public final String product, type, user;
        public final int quantity;
        public final LocalDate date;
        Movement(long id, String product, String type, int quantity, LocalDate date, String user) {
            this.id = id; this.product = product; this.type = type; this.quantity = quantity; this.date = date; this.user = user;
        }
    }

    public User authenticate(String login, char[] password) throws Exception {
        if (login == null || login.trim().isEmpty() || password.length == 0)
            throw new IllegalArgumentException("Informe usuário e senha.");
        String sql = "SELECT id, nome, senha_hash FROM usuarios WHERE login = ? AND ativo = TRUE";
        try (Connection c = connect(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, login.trim());
            try (ResultSet r = p.executeQuery()) {
                if (!r.next() || !verifyPassword(password, r.getString("senha_hash")))
                    throw new IllegalArgumentException("Usuário ou senha incorretos.");
                return new User(r.getLong("id"), r.getString("nome"));
            }
        }
    }

    boolean verifyPassword(char[] password, String stored) throws Exception {
        String[] parts = stored.split(":", -1);
        if (parts.length != 2) return false;
        byte[] salt = hex(parts[0]);
        byte[] expected = hex(parts[1]);
        PBEKeySpec spec = new PBEKeySpec(password, salt, 120000, expected.length * 8);
        try {
            byte[] actual = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return MessageDigest.isEqual(actual, expected);
        } finally { spec.clearPassword(); }
    }

    private byte[] hex(String value) {
        if (value.length() % 2 != 0) throw new IllegalArgumentException("Hash de senha inválido.");
        byte[] result = new byte[value.length() / 2];
        for (int i = 0; i < result.length; i++) {
            int hi = Character.digit(value.charAt(i * 2), 16);
            int lo = Character.digit(value.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) throw new IllegalArgumentException("Hash de senha inválido.");
            result[i] = (byte)((hi << 4) | lo);
        }
        return result;
    }

    public List<Product> products(String query) throws SQLException {
        List<Product> result = new ArrayList<>();
        String sql = "SELECT id, nome, descricao, estoque_atual, estoque_minimo FROM produtos WHERE ativo = TRUE AND nome LIKE ?";
        try (Connection c = connect(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, "%" + (query == null ? "" : query.trim()) + "%");
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) result.add(new Product(r.getLong(1), r.getString(2), r.getString(3), r.getInt(4), r.getInt(5)));
            }
        }
        Logic.sortProducts(result); // Java TimSort: ordenação alfabética exigida no enunciado.
        return result;
    }

    public void createProduct(String name, String description, int initial, int minimum, User user) throws SQLException {
        createProduct(name, description, initial, minimum, LocalDate.now(), user);
    }

    public void createProduct(String name, String description, int initial, int minimum, LocalDate date, User user) throws SQLException {
        validateProduct(name, description, minimum);
        if (initial < 0) throw new IllegalArgumentException("Estoque inicial não pode ser negativo.");
        if (date == null) throw new IllegalArgumentException("Informe a data.");
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            try {
                long id;
                try (PreparedStatement p = c.prepareStatement(
                        "INSERT INTO produtos (nome, descricao, estoque_atual, estoque_minimo) VALUES (?, ?, 0, ?)", Statement.RETURN_GENERATED_KEYS)) {
                    p.setString(1, name.trim()); p.setString(2, description.trim()); p.setInt(3, minimum);
                    p.executeUpdate();
                    try (ResultSet keys = p.getGeneratedKeys()) { keys.next(); id = keys.getLong(1); }
                }
                if (initial > 0) {
                    try (PreparedStatement p = c.prepareStatement("UPDATE produtos SET estoque_atual = ? WHERE id = ?")) {
                        p.setInt(1, initial); p.setLong(2, id); p.executeUpdate();
                    }
                    insertMovement(c, id, user.id, "ENTRADA", initial, date);
                }
                c.commit();
            } catch (Exception e) {
                c.rollback();
                if (e instanceof SQLException) throw (SQLException)e;
                throw e;
            }
        }
    }

    public void updateProduct(long id, String name, String description, int minimum) throws SQLException {
        validateProduct(name, description, minimum);
        try (Connection c = connect(); PreparedStatement p = c.prepareStatement(
                "UPDATE produtos SET nome = ?, descricao = ?, estoque_minimo = ? WHERE id = ? AND ativo = TRUE")) {
            p.setString(1, name.trim()); p.setString(2, description.trim()); p.setInt(3, minimum); p.setLong(4, id);
            if (p.executeUpdate() == 0) throw new IllegalArgumentException("Produto não encontrado.");
        }
    }

    public void deleteProduct(long id) throws SQLException {
        try (Connection c = connect(); PreparedStatement p = c.prepareStatement("UPDATE produtos SET ativo = FALSE WHERE id = ? AND ativo = TRUE")) {
            p.setLong(1, id);
            if (p.executeUpdate() == 0) throw new IllegalArgumentException("Produto não encontrado.");
        }
    }

    public Product move(long productId, String type, int quantity, LocalDate date, User user) throws SQLException {
        if (!type.equals("ENTRADA") && !type.equals("SAIDA")) throw new IllegalArgumentException("Tipo de movimentação inválido.");
        if (quantity <= 0) throw new IllegalArgumentException("Quantidade deve ser maior que zero.");
        if (date == null) throw new IllegalArgumentException("Informe a data.");
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            try {
                Product product;
                try (PreparedStatement p = c.prepareStatement(
                        "SELECT id, nome, descricao, estoque_atual, estoque_minimo FROM produtos WHERE id = ? AND ativo = TRUE FOR UPDATE")) {
                    p.setLong(1, productId);
                    try (ResultSet r = p.executeQuery()) {
                        if (!r.next()) throw new IllegalArgumentException("Produto não encontrado.");
                        product = new Product(r.getLong(1), r.getString(2), r.getString(3), r.getInt(4), r.getInt(5));
                    }
                }
                if (type.equals("SAIDA") && quantity > product.current)
                    throw new IllegalArgumentException("Saldo insuficiente. Disponível: " + product.current + ".");
                long newBalance = (long)product.current + (type.equals("ENTRADA") ? quantity : -quantity);
                if (newBalance > Integer.MAX_VALUE) throw new IllegalArgumentException("Saldo excede o limite permitido.");
                try (PreparedStatement p = c.prepareStatement("UPDATE produtos SET estoque_atual = ? WHERE id = ?")) {
                    p.setInt(1, (int)newBalance); p.setLong(2, productId); p.executeUpdate();
                }
                insertMovement(c, productId, user.id, type, quantity, date);
                c.commit();
                return new Product(product.id, product.name, product.description, (int)newBalance, product.minimum);
            } catch (Exception e) {
                c.rollback();
                if (e instanceof SQLException) throw (SQLException)e;
                throw e;
            }
        }
    }

    private void insertMovement(Connection c, long productId, long userId, String type, int quantity, LocalDate date) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(
                "INSERT INTO movimentacoes (produto_id, usuario_id, tipo, quantidade, data_movimentacao) VALUES (?, ?, ?, ?, ?)")) {
            p.setLong(1, productId); p.setLong(2, userId); p.setString(3, type); p.setInt(4, quantity);
            p.setDate(5, Date.valueOf(date)); p.executeUpdate();
        }
    }

    public List<Movement> movements() throws SQLException {
        List<Movement> result = new ArrayList<>();
        String sql = "SELECT m.id, p.nome, m.tipo, m.quantidade, m.data_movimentacao, u.nome " +
                "FROM movimentacoes m JOIN produtos p ON p.id = m.produto_id JOIN usuarios u ON u.id = m.usuario_id " +
                "ORDER BY m.data_movimentacao DESC, m.id DESC";
        try (Connection c = connect(); PreparedStatement p = c.prepareStatement(sql); ResultSet r = p.executeQuery()) {
            while (r.next()) result.add(new Movement(r.getLong(1), r.getString(2), r.getString(3), r.getInt(4), r.getDate(5).toLocalDate(), r.getString(6)));
        }
        return result;
    }

    private void validateProduct(String name, String description, int minimum) {
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("Nome do produto é obrigatório.");
        if (name.trim().length() > 120) throw new IllegalArgumentException("Nome deve ter até 120 caracteres.");
        if (description == null || description.length() > 255) throw new IllegalArgumentException("Descrição deve ter até 255 caracteres.");
        if (minimum < 0) throw new IllegalArgumentException("Estoque mínimo não pode ser negativo.");
    }
}
