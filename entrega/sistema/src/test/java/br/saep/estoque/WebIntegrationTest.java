package br.saep.estoque;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Testa as rotas HTTP reais com o banco local e apaga o produto temporário. */
public final class WebIntegrationTest {
    private static int checks;
    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static String field(String key, String value) { return encode(key) + "=" + encode(value); }
    private static String csrf(String html) {
        Matcher matcher = Pattern.compile("name='csrf' value='([^']+)'").matcher(html);
        if (!matcher.find()) throw new AssertionError("token CSRF ausente");
        return matcher.group(1);
    }
    private static HttpResponse<String> get(HttpClient client, String base, String path, String cookie) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path)).GET();
        if (cookie != null) builder.header("Cookie", cookie);
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
    private static HttpResponse<String> post(HttpClient client, String base, String path, String body, String cookie) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path))
            .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (cookie != null) builder.header("Cookie", cookie);
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
    public static void main(String[] args) throws Exception {
        WebApp app = new WebApp("127.0.0.1", 0);
        app.start();
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        String base = "http://127.0.0.1:" + app.port();
        String name = "TESTE_WEB_" + System.nanoTime();
        long productId = -1;
        long externalProductId = -1;
        try {
            check(get(client, base, "/login", null).statusCode() == 200, "login acessível");
            check(get(client, base, "/", null).statusCode() == 303, "área interna protegida");
            HttpResponse<String> bad = post(client, base, "/login", field("login", "administrador") + "&" + field("senha", "errada"), null);
            check(bad.statusCode() == 200 && bad.body().contains("incorretos"), "erro de autenticação");
            HttpResponse<String> login = post(client, base, "/login", field("login", "administrador") + "&" + field("senha", "Saep@2026"), null);
            check(login.statusCode() == 303, "login válido redireciona");
            String cookie = login.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0];
            HttpResponse<String> home = get(client, base, "/", cookie);
            check(home.statusCode() == 200 && home.body().contains("Administrador"), "painel inicial e usuário");
            HttpResponse<String> products = get(client, base, "/produtos", cookie);
            check(products.statusCode() == 200 && products.body().contains("Martelo"), "listagem de produtos");
            String token = csrf(products.body());
            check(get(client, base, "/assets/style.css", null).statusCode() == 200, "CSS disponível");
            check(post(client, base, "/produtos/criar", field("nome", "sem token"), cookie).statusCode() == 403, "CSRF bloqueado");
            String invalid = field("csrf", token) + "&" + field("nome", "") + "&" + field("inicial", "0") + "&" + field("minimo", "0");
            check(post(client, base, "/produtos/criar", invalid, cookie).headers().firstValue("Location").orElse("").contains("erro"), "nome obrigatório validado");
            String invalidNumber = field("csrf", token) + "&" + field("nome", "Inválido") + "&" + field("inicial", "0") + "&" + field("minimo", "-1");
            check(post(client, base, "/produtos/criar", invalidNumber, cookie).headers().firstValue("Location").orElse("").contains("erro"), "mínimo negativo validado");

            String create = field("csrf", token) + "&" + field("nome", name) + "&" + field("descricao", "Teste web") + "&" + field("inicial", "5") + "&" + field("minimo", "8");
            check(post(client, base, "/produtos/criar", create, cookie).statusCode() == 303, "cadastro pelo site");
            Store store = new Store();
            Store.Product product = store.products(name).get(0);
            productId = product.id;
            check(product.current == 5, "saldo inicial salvo");
            String externalName = name + "_EXTERNO";
            check(get(client, base, "/estoque", cookie).body().contains("action='/estoque/novo-item'"), "formulário de item externo acessível");
            String external = field("csrf", token) + "&" + field("nome", externalName) + "&" + field("descricao", "Recebido de fora") +
                "&" + field("quantidade", "7") + "&" + field("minimo", "2") + "&" + field("data", "23/09/2026");
            check(post(client, base, "/estoque/novo-item", external, cookie).headers().firstValue("Location").orElse("").contains("ok"), "cadastro e entrada de item externo");
            Store.Product externalProduct = store.products(externalName).get(0);
            externalProductId = externalProduct.id;
            check(externalProduct.current == 7 && externalProduct.minimum == 2, "saldo externo salvo no MySQL");
            check(store.movements().stream().anyMatch(m -> m.product.equals(externalName) && m.type.equals("ENTRADA") && m.quantity == 7 && m.date.toString().equals("2026-09-23")), "entrada externa registrada na data informada");
            String invalidExternal = field("csrf", token) + "&" + field("nome", name + "_INVALIDO") + "&" + field("quantidade", "0") + "&" + field("minimo", "0") + "&" + field("data", "23/09/2026");
            check(post(client, base, "/estoque/novo-item", invalidExternal, cookie).headers().firstValue("Location").orElse("").contains("erro"), "quantidade externa zero recusada");
            check(store.products(name + "_INVALIDO").isEmpty(), "entrada inválida não cria produto");
            check(get(client, base, "/produtos?busca=" + encode(name), cookie).body().contains(name), "busca pelo site");
            check(get(client, base, "/produtos?busca=", cookie).body().contains("Martelo"), "limpar busca mostra todos");
            check(get(client, base, "/produtos/editar?id=" + productId, cookie).body().contains("Salvar alterações"), "formulário de edição acessível");

            String edit = field("csrf", token) + "&" + field("id", String.valueOf(productId)) + "&" + field("nome", name + "_EDITADO") + "&" + field("descricao", "Editado") + "&" + field("minimo", "8");
            check(post(client, base, "/produtos/editar", edit, cookie).statusCode() == 303, "edição pelo site");
            check(store.products(name + "_EDITADO").get(0).description.equals("Editado"), "edição persistida");

            String exit = field("csrf", token) + "&" + field("produto_id", String.valueOf(productId)) + "&" + field("tipo", "SAIDA") + "&" + field("quantidade", "2") + "&" + field("data", "24/09/2026");
            HttpResponse<String> move = post(client, base, "/estoque/movimentar", exit, cookie);
            check(move.statusCode() == 303 && move.headers().firstValue("Location").orElse("").contains("alerta"), "saída dispara alerta");
            check(store.products(name + "_EDITADO").get(0).current == 3, "saldo após saída");
            String alertPath = move.headers().firstValue("Location").orElseThrow();
            check(get(client, base, alertPath, cookie).body().contains("ATENÇÃO"), "alerta visível na página");

            String entry = field("csrf", token) + "&" + field("produto_id", String.valueOf(productId)) + "&" + field("tipo", "ENTRADA") + "&" + field("quantidade", "10") + "&" + field("data", "24/09/2026");
            check(post(client, base, "/estoque/movimentar", entry, cookie).statusCode() == 303, "entrada pelo site");
            check(store.products(name + "_EDITADO").get(0).current == 13, "saldo após entrada");
            String tooMuch = field("csrf", token) + "&" + field("produto_id", String.valueOf(productId)) + "&" + field("tipo", "SAIDA") + "&" + field("quantidade", "14") + "&" + field("data", "24/09/2026");
            check(post(client, base, "/estoque/movimentar", tooMuch, cookie).headers().firstValue("Location").orElse("").contains("erro"), "saída sem saldo recusada");
            check(store.products(name + "_EDITADO").get(0).current == 13, "erro preserva saldo");
            String history = get(client, base, "/historico", cookie).body();
            check(history.contains(name + "_EDITADO"), "histórico pelo site");
            check(history.contains("Administrador"), "responsável mostrado no histórico");
            check(get(client, base, "/produtos?busca=" + encode("<script>"), cookie).body().contains("&lt;script&gt;"), "busca escapada no HTML");

            String deletion = field("csrf", token) + "&" + field("id", String.valueOf(productId));
            check(get(client, base, "/produtos", cookie).body().contains("return confirm("), "exclusão exige confirmação no navegador");
            check(post(client, base, "/produtos/excluir", deletion, cookie).statusCode() == 303, "exclusão pelo site");
            check(store.products(name + "_EDITADO").isEmpty(), "produto oculto após exclusão");
            check(post(client, base, "/logout", field("csrf", token), cookie).statusCode() == 303, "logout pelo site");
            check(get(client, base, "/", cookie).statusCode() == 303, "sessão invalidada");
            System.out.println("PASSOU: " + checks + " verificações do site com MariaDB.");
        } finally {
            app.stop();
            if (productId >= 0 || externalProductId >= 0) {
                String url = System.getenv().getOrDefault("SAEP_DB_URL", "jdbc:mariadb://localhost:3306/saep_db");
                String dbUser = System.getenv().getOrDefault("SAEP_DB_USER", "root");
                String dbPassword = System.getenv().getOrDefault("SAEP_DB_PASSWORD", "");
                try (Connection c = DriverManager.getConnection(url, dbUser, dbPassword)) {
                    for (long id : new long[]{productId, externalProductId}) {
                        if (id < 0) continue;
                        try (PreparedStatement p = c.prepareStatement("DELETE FROM movimentacoes WHERE produto_id = ?")) {
                            p.setLong(1, id); p.executeUpdate();
                        }
                        try (PreparedStatement p = c.prepareStatement("DELETE FROM produtos WHERE id = ?")) {
                            p.setLong(1, id); p.executeUpdate();
                        }
                    }
                }
            }
        }
    }
}
