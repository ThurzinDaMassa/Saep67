package br.saep.estoque;

import java.security.MessageDigest;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
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
