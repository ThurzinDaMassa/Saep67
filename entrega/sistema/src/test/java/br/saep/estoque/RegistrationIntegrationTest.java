package br.saep.estoque;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Exercita cadastro e autenticação reais; remove apenas a conta temporária criada pelo teste. */
public final class RegistrationIntegrationTest {
    private static int checks;
    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static HttpResponse<String> get(HttpClient client, String base, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private static HttpResponse<String> post(HttpClient client, String base, String path, String form) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base + path))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private static HttpResponse<String> register(HttpClient client, String base, String name, String login, String password, String confirmation) throws Exception {
        String html = get(client, base, "/cadastro").body();
        Matcher csrf = Pattern.compile("name='csrf' value='([^']+)'").matcher(html);
        if (!csrf.find()) throw new AssertionError("CSRF presente no cadastro");
        return post(client, base, "/cadastro", "csrf=" + encode(csrf.group(1)) + "&nome=" + encode(name) +
            "&login=" + encode(login) + "&senha=" + encode(password) + "&confirmacao=" + encode(confirmation));
    }
    public static void main(String[] args) throws Exception {
        WebApp app = new WebApp("127.0.0.1", 0);
        app.start();
        String base = "http://127.0.0.1:" + app.port();
        String login = "TESTE_CADASTRO_" + System.nanoTime();
        String password = "Cadastro@Teste2026";
        HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        try {
            check(get(client, base, "/login").body().contains("href='/cadastro'"), "login oferece cadastro");
            check(get(client, base, "/cadastro").statusCode() == 200, "cadastro público acessível");
            check(post(client, base, "/cadastro", "nome=Teste&login=" + login + "&senha=" + password).statusCode() == 403, "cadastro sem CSRF bloqueado");
            check(post(HttpClient.newHttpClient(), base, "/cadastro", "csrf=falso&nome=Teste").statusCode() == 403, "cadastro sem cookie bloqueado");
            check(register(client, base, "", login, password, password).statusCode() == 400, "nome obrigatório");
            check(register(client, base, "a".repeat(101), login, password, password).statusCode() == 400, "limite do nome");
            check(register(client, base, "Teste", "ab", password, password).statusCode() == 400, "usuário curto rejeitado");
            check(register(client, base, "Teste", "com espaço", password, password).statusCode() == 400, "caracteres de usuário validados");
            check(register(client, base, "Teste", "a".repeat(51), password, password).statusCode() == 400, "limite do usuário");
            check(register(client, base, "Teste", login, "curta", "curta").statusCode() == 400, "senha curta rejeitada");
            check(register(client, base, "Teste", login, "a".repeat(129), "a".repeat(129)).statusCode() == 400, "limite da senha");
            HttpResponse<String> mismatch = register(client, base, "<script>Teste</script>", login, password, "OutraSenha2026");
            check(mismatch.statusCode() == 400 && mismatch.body().contains("não coincidem"), "confirmação de senha validada");
            check(mismatch.body().contains("&lt;script&gt;Teste&lt;/script&gt;") && mismatch.body().contains("value='" + login + "'"), "campos preservados e escapados");
            check(!mismatch.body().contains(password), "senha não reapresentada no HTML");
            HttpResponse<String> created = register(client, base, "  Pessoa de Teste  ", "  " + login + "  ", password, password);
            check(created.statusCode() == 303 && created.headers().firstValue("Location").orElse("").equals("/login?cadastro=sucesso"), "cadastro válido retorna ao login");
            check(get(client, base, "/login?cadastro=sucesso").body().contains("Conta criada com sucesso"), "confirmação visível");
            check(get(client, base, "/").statusCode() == 303, "cadastro mantém área interna protegida até login");
            HttpResponse<String> duplicate = register(client, base, "Teste", login.toLowerCase(java.util.Locale.ROOT), password, password);
            check(duplicate.statusCode() == 400 && duplicate.body().contains("já está cadastrado"), "usuário duplicado tratado sem alterar conta");
            try (Connection c = connect(); PreparedStatement p = c.prepareStatement("SELECT nome, senha_hash, perfil FROM usuarios WHERE login = ?")) {
                p.setString(1, login);
                try (ResultSet r = p.executeQuery()) {
                    check(r.next() && r.getString(1).equals("Pessoa de Teste"), "conta persistida com nome normalizado");
                    check(r.getString(2).matches("[a-f0-9]{32}:[a-f0-9]{64}") && !r.getString(2).contains(password), "senha armazenada com salt e hash");
                    check(r.getString(3).equals("OPERADOR"), "perfil Operador aplicado pelo servidor");
                    check(!r.next(), "apenas uma conta criada");
                }
            }
            check(post(client, base, "/login", "login=" + encode(login) + "&senha=incorreta").body().contains("incorretos"), "senha incorreta rejeitada");
            check(post(client, base, "/login", "login=" + encode(login) + "&senha=" + encode(password)).statusCode() == 303, "nova conta autentica");
            check(get(client, base, "/").body().contains("Pessoa de Teste"), "dashboard exibe nova conta");
            HttpResponse<String> profile = get(client, base, "/perfil");
            check(profile.statusCode() == 200 && profile.body().contains(login), "perfil disponível para a nova conta");
            System.out.println("PASSOU: " + checks + " verificações do cadastro com MariaDB.");
        } finally {
            app.stop();
            try (Connection c = connect(); PreparedStatement p = c.prepareStatement("DELETE FROM usuarios WHERE login = ?")) {
                p.setString(1, login); p.executeUpdate();
            }
        }
    }
    private static Connection connect() throws Exception {
        return DriverManager.getConnection(System.getenv().getOrDefault("SAEP_DB_URL", "jdbc:mariadb://localhost:3306/saep_db"),
            System.getenv().getOrDefault("SAEP_DB_USER", "root"), System.getenv().getOrDefault("SAEP_DB_PASSWORD", ""));
    }
}
