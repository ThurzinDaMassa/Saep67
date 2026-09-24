package br.saep.estoque;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;

/** Testa o banco real. Remove apenas o produto de teste criado nesta execução. */
public final class IntegrationTest {
    private static int checks;
    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }

    public static void main(String[] args) throws Exception {
        Store store = new Store();
        String name = "TESTE_SAEP_" + System.nanoTime();
        long productId = -1;
        try {
            Store.User user = store.authenticate("administrador", "Saep@2026".toCharArray());
            check(user.id == 1, "login válido");
            boolean refused = false;
            try { store.authenticate("administrador", "senha errada".toCharArray()); }
            catch (IllegalArgumentException e) { refused = true; }
            check(refused, "login inválido recusado");
            check(store.products("").size() >= 3, "listagem inicial");
            check(store.products("Martelo").stream().anyMatch(p -> p.name.equals("Martelo")), "busca");

            store.createProduct(name, "Produto de teste", 5, 8, user);
            Store.Product created = store.products(name).get(0);
            productId = created.id;
            check(created.current == 5, "cadastro e estoque inicial");
            check(created.minimum == 8, "estoque mínimo");
            check(Logic.belowMinimum(created.current, created.minimum), "condição de alerta");

            store.updateProduct(productId, name + "_EDITADO", "Editado", 8);
            check(store.products(name + "_EDITADO").get(0).description.equals("Editado"), "edição");
            LocalDate date = LocalDate.of(2026, 9, 24);
            Store.Product afterOut = store.move(productId, "SAIDA", 2, date, user);
            check(afterOut.current == 3, "saída");
            check(Logic.belowMinimum(afterOut.current, afterOut.minimum), "alerta após saída");
            Store.Product afterIn = store.move(productId, "ENTRADA", 10, date, user);
            check(afterIn.current == 13, "entrada");
            refused = false;
            try { store.move(productId, "SAIDA", 14, date, user); }
            catch (IllegalArgumentException e) { refused = true; }
            check(refused, "saída sem saldo recusada");
            check(store.products(name + "_EDITADO").get(0).current == 13, "saldo preservado após erro");

            String url = System.getenv().getOrDefault("SAEP_DB_URL", "jdbc:mariadb://localhost:3306/saep_db");
            String dbUser = System.getenv().getOrDefault("SAEP_DB_USER", "root");
            String dbPassword = System.getenv().getOrDefault("SAEP_DB_PASSWORD", "");
            try (Connection c = DriverManager.getConnection(url, dbUser, dbPassword);
                 PreparedStatement p = c.prepareStatement("SELECT COUNT(*), MIN(usuario_id), MAX(usuario_id), MIN(data_movimentacao), MAX(data_movimentacao) FROM movimentacoes WHERE produto_id = ?")) {
                p.setLong(1, productId);
                try (ResultSet r = p.executeQuery()) {
                    r.next();
                    check(r.getInt(1) == 3, "três movimentações sem registro da saída recusada");
                    check(r.getLong(2) == user.id && r.getLong(3) == user.id, "responsável registrado");
                    check(date.equals(r.getDate(4).toLocalDate()) && date.equals(r.getDate(5).toLocalDate()), "data registrada");
                }
            }
            check(store.movements().stream().anyMatch(m -> m.product.equals(name + "_EDITADO")), "histórico consultável");
            store.deleteProduct(productId);
            check(store.products(name + "_EDITADO").isEmpty(), "exclusão lógica");
            check(store.movements().stream().anyMatch(m -> m.product.equals(name + "_EDITADO")), "histórico após exclusão");
            System.out.println("PASSOU: " + checks + " verificações de integração com MariaDB.");
        } finally {
            if (productId >= 0) {
                String url = System.getenv().getOrDefault("SAEP_DB_URL", "jdbc:mariadb://localhost:3306/saep_db");
                String dbUser = System.getenv().getOrDefault("SAEP_DB_USER", "root");
                String dbPassword = System.getenv().getOrDefault("SAEP_DB_PASSWORD", "");
                try (Connection c = DriverManager.getConnection(url, dbUser, dbPassword)) {
                    try (PreparedStatement p = c.prepareStatement("DELETE FROM movimentacoes WHERE produto_id = ?")) {
                        p.setLong(1, productId); p.executeUpdate();
                    }
                    try (PreparedStatement p = c.prepareStatement("DELETE FROM produtos WHERE id = ?")) {
                        p.setLong(1, productId); p.executeUpdate();
                    }
                }
            }
        }
    }
}
