package br.saep.estoque;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
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
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/** Exercita o perfil por HTTP usando um usuário temporário, removido ao final. */
public final class ProfileIntegrationTest {
    private static int checks;
    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }
    private static String field(String key, String value) {
        return URLEncoder.encode(key, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
    private static String csrf(String html) {
        Matcher matcher = Pattern.compile("name='csrf' value='([^']+)'").matcher(html);
        if (!matcher.find()) throw new AssertionError("token CSRF ausente");
        return matcher.group(1);
    }
    private static HttpResponse<String> get(HttpClient client, String base, String path, String cookie) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base + path)).header("Cookie", cookie).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private static HttpResponse<String> post(HttpClient client, String base, String path, String body, String cookie) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base + path)).header("Cookie", cookie)
            .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body)).build(),
            HttpResponse.BodyHandlers.ofString());
    }
    private static HttpResponse<String> login(HttpClient client, String base, String user, String password) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base + "/login"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(field("login", user) + "&" + field("senha", password))).build(),
            HttpResponse.BodyHandlers.ofString());
    }
    private static byte[] png() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0x16736d);
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
    private static HttpResponse<String> upload(HttpClient client, String base, String path, String cookie, String token, byte[] image) throws Exception {
        String boundary = "SAEPtestBoundary";
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"csrf\"\r\n\r\n" + token + "\r\n" +
            "--" + boundary + "\r\nContent-Disposition: form-data; name=\"imagem\"; filename=\"test.png\"\r\n" +
            "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(image);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return client.send(HttpRequest.newBuilder(URI.create(base + path)).header("Cookie", cookie)
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(), HttpResponse.BodyHandlers.ofString());
    }
    public static void main(String[] args) throws Exception {
        String url = System.getenv().getOrDefault("SAEP_DB_URL", "jdbc:mariadb://localhost:3306/saep_db");
        String dbUser = System.getenv().getOrDefault("SAEP_DB_USER", "root");
        String dbPassword = System.getenv().getOrDefault("SAEP_DB_PASSWORD", "");
        String username = "perfil_teste_" + Long.toString(System.nanoTime(), 36);
        long userId = -1;
        WebApp app = null;
        try {
            try (Connection c = DriverManager.getConnection(url, dbUser, dbPassword);
                 PreparedStatement p = c.prepareStatement("INSERT INTO usuarios (nome, login, senha_hash, perfil) " +
                     "SELECT 'Perfil Teste', ?, senha_hash, 'OPERADOR' FROM usuarios WHERE login = 'administrador'", Statement.RETURN_GENERATED_KEYS)) {
                p.setString(1, username);
                check(p.executeUpdate() == 1, "usuário temporário criado");
                try (ResultSet keys = p.getGeneratedKeys()) { keys.next(); userId = keys.getLong(1); }
            }
            app = new WebApp("127.0.0.1", 0); app.start();
            HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
            String base = "http://127.0.0.1:" + app.port();
            HttpResponse<String> signedIn = login(client, base, username, "Saep@2026");
            check(signedIn.statusCode() == 303, "login temporário");
            String cookie = signedIn.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0];
            HttpResponse<String> profile = get(client, base, "/perfil", cookie);
            check(profile.statusCode() == 200 && profile.body().contains("Meu perfil"), "aba de perfil");
            String token = csrf(profile.body());
            String updatedLogin = username + "x";
            String edit = field("csrf", token) + "&" + field("nome", "Pessoa Teste") + "&" + field("login", updatedLogin) +
                "&" + field("cargo", "Técnico de manutenção") + "&" + field("bio", "Cuida do estoque <teste>.");
            check(post(client, base, "/perfil/atualizar", edit, cookie).statusCode() == 303, "edição aceita");
            String html = get(client, base, "/perfil", cookie).body();
            check(html.contains("Pessoa Teste") && html.contains("Técnico de manutenção") && html.contains("&lt;teste&gt;"), "dados persistidos e bio escapada");
            check(!html.contains("<teste>"), "bio sem HTML executável");
            check(html.contains("Operador"), "permissão separada da função");
            check(post(client, base, "/perfil/atualizar", field("nome", "Sem token"), cookie).statusCode() == 403, "CSRF em dados");
            byte[] image = png();
            check(upload(client, base, "/perfil/foto", cookie, token, image).statusCode() == 303, "upload de foto");
            check(upload(client, base, "/perfil/banner", cookie, token, image).statusCode() == 303, "upload de banner");
            HttpResponse<byte[]> photo = client.send(HttpRequest.newBuilder(URI.create(base + "/perfil/foto")).header("Cookie", cookie).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            check(photo.statusCode() == 200 && photo.headers().firstValue("Content-Type").orElse("").equals("image/png"), "foto acessível pela conta");
            check(client.send(HttpRequest.newBuilder(URI.create(base + "/perfil/foto")).GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode() == 303, "foto protegida");
            check(upload(client, base, "/perfil/foto", cookie, token, "arquivo falso".getBytes(StandardCharsets.UTF_8)).headers().firstValue("Location").orElse("").contains("erro"), "arquivo inválido recusado");
            check(post(client, base, "/perfil/remover-foto", field("csrf", token), cookie).statusCode() == 303, "remoção de foto");
            check(client.send(HttpRequest.newBuilder(URI.create(base + "/perfil/foto")).header("Cookie", cookie).GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode() == 404, "foto removida");
            String wrong = field("csrf", token) + "&" + field("atual", "errada") + "&" + field("nova", "NovaSenha@2026") + "&" + field("confirmacao", "NovaSenha@2026");
            check(post(client, base, "/perfil/senha", wrong, cookie).headers().firstValue("Location").orElse("").contains("erro"), "senha atual exigida");
            String change = field("csrf", token) + "&" + field("atual", "Saep@2026") + "&" + field("nova", "NovaSenha@2026") + "&" + field("confirmacao", "NovaSenha@2026");
            check(post(client, base, "/perfil/senha", change, cookie).headers().firstValue("Location").orElse("").contains("ok"), "senha atualizada");
            check(login(client, base, updatedLogin, "Saep@2026").statusCode() == 200, "senha antiga recusada");
            check(login(client, base, updatedLogin, "NovaSenha@2026").statusCode() == 303, "login com nova senha");
            check(post(client, base, "/perfil/remover-banner", field("csrf", token), cookie).statusCode() == 303, "remoção de banner");
            check(get(client, base, "/perfil", cookie).body().contains("SAEP <span>/</span> PERFIL"), "banner padrão restaurado");
            System.out.println("PASSOU: " + checks + " verificações do perfil com MariaDB.");
        } finally {
            if (app != null) app.stop();
            if (userId >= 0) try (Connection c = DriverManager.getConnection(url, dbUser, dbPassword);
                PreparedStatement p = c.prepareStatement("DELETE FROM usuarios WHERE id = ?")) {
                p.setLong(1, userId); p.executeUpdate();
            }
        }
    }
}
