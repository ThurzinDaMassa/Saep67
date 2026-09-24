package br.saep.estoque;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Site local servido pelo JDK, com HTML renderizado no servidor e banco MariaDB/MySQL. */
public final class WebApp {
    private final Store store = new Store();
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final HttpServer server;

    private static final class Session {
        final Store.User user;
        final String csrf;
        final long createdAt;
        Session(Store.User user, String csrf) {
            this.user = user; this.csrf = csrf; this.createdAt = System.currentTimeMillis();
        }
    }

    public WebApp(String host, int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(host, port), 0);
        server.createContext("/", this::handle);
    }

    public void start() { server.start(); }
    public void stop() { server.stop(0); }
    public int port() { return server.getAddress().getPort(); }

    public static void main(String[] args) throws Exception {
        String host = System.getenv().getOrDefault("SAEP_WEB_HOST", "127.0.0.1");
        int port = Integer.parseInt(System.getenv().getOrDefault("SAEP_WEB_PORT", "8080"));
        WebApp app = new WebApp(host, port);
        app.start();
        System.out.println("Sistema SAEP disponível em http://" + host + ":" + port);
        System.out.println("Pressione Ctrl+C para encerrar.");
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            if (path.equals("/assets/style.css") && method.equals("GET")) { css(exchange); return; }
            if (path.equals("/login") && method.equals("GET")) { loginPage(exchange, ""); return; }
            if (path.equals("/login") && method.equals("POST")) { login(exchange); return; }
            Session session = session(exchange);
            if (session == null) { redirect(exchange, "/login"); return; }
            if (method.equals("POST")) {
                Map<String, String> form = form(exchange);
                if (!session.csrf.equals(form.get("csrf"))) { respond(exchange, 403, page("Acesso negado", session, "", "<div class='notice error'>Formulário inválido. Recarregue a página.</div>")); return; }
                if (path.equals("/logout")) { logout(exchange); return; }
                if (path.equals("/produtos/criar")) { createProduct(exchange, session, form); return; }
                if (path.equals("/produtos/editar")) { editProduct(exchange, form); return; }
                if (path.equals("/produtos/excluir")) { deleteProduct(exchange, form); return; }
                if (path.equals("/estoque/movimentar")) { move(exchange, session, form); return; }
                if (path.equals("/estoque/novo-item")) { receiveNewItem(exchange, session, form); return; }
            } else if (method.equals("GET")) {
                if (path.equals("/")) { dashboard(exchange, session); return; }
                if (path.equals("/produtos")) { products(exchange, session); return; }
                if (path.equals("/produtos/editar")) { editPage(exchange, session); return; }
                if (path.equals("/estoque")) { stock(exchange, session); return; }
                if (path.equals("/historico")) { history(exchange, session); return; }
            }
            respond(exchange, 404, page("Página não encontrada", session, "", "<div class='notice error'>Página não encontrada.</div>"));
        } catch (IllegalArgumentException e) {
            respond(exchange, 400, page("Dados inválidos", session(exchange), "", "<div class='notice error'>" + esc(e.getMessage()) + "</div>"));
        } catch (Exception e) {
            e.printStackTrace();
            respond(exchange, 500, page("Erro", session(exchange), "", "<div class='notice error'>Não foi possível concluir a operação. Verifique o banco de dados e tente novamente.</div>"));
        } finally { exchange.close(); }
    }

    private void loginPage(HttpExchange exchange, String error) throws IOException {
        String body = "<main class='login-layout'><section class='login-showcase'>" +
                "<div class='showcase-brand'><span class='brand-symbol'>" + icon("package") + "</span><span>SAEP <small>CONTROLE DE ESTOQUE</small></span></div>" +
                "<div class='showcase-content'><p class='eyebrow'>SAEP / CONTROLE DE ESTOQUE</p><h2>Ferramentas, saldos e movimentações em um só lugar.</h2>" +
                "<p>Consulte o catálogo, registre operações e acompanhe o estoque do almoxarifado com clareza.</p>" +
                "<div class='showcase-points'><span>" + icon("package") + " Catálogo organizado</span><span>" + icon("arrows") + " Movimentações rastreáveis</span><span>" + icon("alert") + " Alertas de estoque</span></div></div>" +
                "<div class='showcase-footer'>SIMULADO SAEP <span>•</span> GESTÃO DE FERRAMENTAS</div></section>" +
                "<section class='login-side'><div class='login-card'><div class='login-mark'>" + icon("shield") + "</div>" +
                "<p class='eyebrow'>ACESSO AO SISTEMA</p><h1>Entre na sua conta</h1>" +
                "<p class='muted'>Entre com suas credenciais para continuar.</p>" +
                (error.isEmpty() ? "" : "<div class='notice error'>" + icon("alert") + "<span>" + esc(error) + "</span></div>") +
                "<form method='post' action='/login' class='form-stack'>" +
                "<label>Usuário<input name='login' autocomplete='username' required maxlength='50' placeholder='Seu usuário' autofocus></label>" +
                "<label>Senha<input type='password' name='senha' autocomplete='current-password' required placeholder='Sua senha'></label>" +
                "<button class='button primary full' type='submit'>Entrar no sistema" + icon("arrow-right") + "</button></form>" +
                "<div class='login-helper'>" + icon("info") + "<span>Para o simulado, use uma das contas demonstrativas: administrador, almoxarife ou operador.</span></div>" +
                "</div></section></main>";
        respond(exchange, 200, page("Entrar", null, "login", body));
    }

    private void login(HttpExchange exchange) throws Exception {
        Map<String, String> values = form(exchange);
        char[] password = values.getOrDefault("senha", "").toCharArray();
        try {
            Store.User user = store.authenticate(values.get("login"), password);
            String token = token();
            sessions.put(token, new Session(user, token()));
            exchange.getResponseHeaders().add("Set-Cookie", "SAEP_SESSION=" + token + "; Path=/; HttpOnly; SameSite=Lax");
            redirect(exchange, "/");
        } catch (IllegalArgumentException e) { loginPage(exchange, e.getMessage()); }
        finally { Arrays.fill(password, '\0'); }
    }

    private void logout(HttpExchange exchange) throws IOException {
        String token = cookie(exchange, "SAEP_SESSION");
        if (token != null) sessions.remove(token);
        exchange.getResponseHeaders().add("Set-Cookie", "SAEP_SESSION=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0");
        redirect(exchange, "/login");
    }

    private void dashboard(HttpExchange exchange, Session session) throws Exception {
        List<Store.Product> products = store.products("");
        List<Store.Movement> movements = store.movements();
        long low = products.stream().filter(p -> Logic.belowMinimum(p.current, p.minimum)).count();
        StringBuilder body = new StringBuilder();
        body.append("<div class='page-heading'><div><p class='eyebrow'>PAINEL / VISÃO GERAL</p><h1>Controle de estoque</h1><p class='muted'>Resumo do catálogo e das operações recentes.</p></div><div class='heading-actions'><a class='button subtle' href='/estoque#novo-item'>")
            .append(icon("package")).append(" Receber item novo</a><a class='button primary' href='/estoque'>")
            .append(icon("plus")).append(" Nova movimentação</a></div></div>");
        body.append("<div class='stats'><div class='stat'><div class='stat-head'><span>Produtos ativos</span><span class='stat-icon'>").append(icon("package")).append("</span></div><strong>").append(products.size()).append("</strong><small>Itens no catálogo</small></div>");
        body.append("<div class='stat warning'><div class='stat-head'><span>Abaixo do mínimo</span><span class='stat-icon'>").append(icon("alert")).append("</span></div><strong>").append(low).append("</strong><small>Precisam de atenção</small></div>");
        body.append("<div class='stat'><div class='stat-head'><span>Movimentações</span><span class='stat-icon'>").append(icon("arrows")).append("</span></div><strong>").append(movements.size()).append("</strong><small>Registros no histórico</small></div></div>");
        body.append("<div class='dashboard-grid'><section class='panel'><div class='panel-head'><div class='panel-heading-icon'>").append(icon("alert")).append("</div><div><h2>Atenção ao estoque</h2><p class='muted'>Produtos abaixo do limite definido.</p></div><a class='text-link' href='/estoque'>Ver estoque ").append(icon("arrow-right")).append("</a></div>");
        if (low == 0) {
            body.append("<div class='empty positive'>").append(icon("check")).append("<div><strong>Estoque em dia</strong><span>Nenhum produto está abaixo do estoque mínimo.</span></div></div>");
            if (!products.isEmpty()) {
                body.append("<div class='mini-heading'>SALDO ATUAL DOS PRODUTOS</div><div class='table-wrap'><table><thead><tr><th>Produto</th><th>Saldo</th><th>Mínimo</th></tr></thead><tbody>");
                for (int i = 0; i < Math.min(5, products.size()); i++) {
                    Store.Product p = products.get(i);
                    body.append("<tr><td><strong>").append(esc(p.name)).append("</strong></td><td class='number-cell'>")
                        .append(p.current).append("</td><td class='number-cell'>").append(p.minimum).append("</td></tr>");
                }
                body.append("</tbody></table></div>");
            }
        }
        else {
            body.append("<div class='table-wrap'><table><thead><tr><th>Produto</th><th>Saldo atual</th><th>Mínimo</th></tr></thead><tbody>");
            for (Store.Product p : products) if (Logic.belowMinimum(p.current, p.minimum))
                body.append("<tr><td><strong>").append(esc(p.name)).append("</strong></td><td class='number-cell danger-number'>").append(p.current).append("</td><td class='number-cell'>").append(p.minimum).append("</td></tr>");
            body.append("</tbody></table></div>");
        }
        body.append("</section><section class='panel'><div class='panel-head'><div class='panel-heading-icon'>").append(icon("clock")).append("</div><div><h2>Últimas movimentações</h2><p class='muted'>Atividade recente registrada no sistema.</p></div><a class='text-link' href='/historico'>Ver histórico ").append(icon("arrow-right")).append("</a></div>");
        if (movements.isEmpty()) body.append("<div class='empty'>Nenhuma movimentação registrada.</div>");
        else {
            body.append("<div class='table-wrap'><table><thead><tr><th>Produto</th><th>Tipo</th><th>Qtd.</th><th>Data</th></tr></thead><tbody>");
            for (int i = 0; i < Math.min(5, movements.size()); i++) {
                Store.Movement m = movements.get(i);
                body.append("<tr><td><strong>").append(esc(m.product)).append("</strong></td><td><span class='badge ")
                    .append(m.type.equals("ENTRADA") ? "ok'>Entrada" : "neutral'>Saída")
                    .append("</span></td><td class='number-cell'>").append(m.quantity).append("</td><td>").append(Logic.DATE.format(m.date)).append("</td></tr>");
            }
            body.append("</tbody></table></div>");
        }
        body.append("</section></div>");
        respond(exchange, 200, page("Início", session, "home", body.toString()));
    }

    private void products(HttpExchange exchange, Session session) throws Exception {
        Map<String, String> query = query(exchange);
        String search = query.getOrDefault("busca", "");
        List<Store.Product> products = store.products(search);
        StringBuilder body = new StringBuilder();
        body.append("<div class='page-heading'><div><p class='eyebrow'>CATÁLOGO</p><h1>Produtos</h1><p class='muted'>Organize os itens e acompanhe seus saldos.</p></div><a class='button subtle' href='/historico'>")
            .append(icon("clock")).append(" Ver histórico</a></div>");
        body.append(flash(query));
        body.append("<div class='grid-two'><section class='panel'><div class='panel-head'><div class='panel-heading-icon'>").append(icon("package")).append("</div><div><h2>Produtos cadastrados</h2><p class='muted'>Catálogo ativo de ferramentas.</p></div><span class='count'>").append(products.size()).append(" itens</span></div>");
        body.append("<form class='search-row' method='get' action='/produtos'><span class='search-field'>").append(icon("search")).append("<input name='busca' value='").append(esc(search)).append("' placeholder='Pesquisar produto'></span><button class='button subtle'>Buscar</button><a class='text-link' href='/produtos'>Limpar</a></form>");
        if (products.isEmpty()) body.append("<div class='empty'>Nenhum produto encontrado.</div>");
        else {
            body.append("<div class='table-wrap'><table><thead><tr><th>Produto</th><th>Saldo</th><th>Mínimo</th><th>Situação</th><th>Ações</th></tr></thead><tbody>");
            for (Store.Product p : products) {
                boolean low = Logic.belowMinimum(p.current, p.minimum);
                body.append("<tr><td><strong>").append(esc(p.name)).append("</strong><small>").append(esc(p.description)).append("</small></td><td>").append(p.current).append("</td><td>").append(p.minimum).append("</td><td><span class='badge ").append(low ? "low'>Abaixo do mínimo" : "ok'>Normal").append("</span></td><td>");
                body.append("<a class='action-link' href='/produtos/editar?id=").append(p.id).append("' title='Editar produto'>").append(icon("edit")).append(" <span>Editar</span></a>");
                body.append("<form method='post' action='/produtos/excluir' onsubmit=\"return confirm('Excluir este produto? O histórico será preservado.');\" class='inline-form'>")
                    .append(hidden(session)).append("<input type='hidden' name='id' value='").append(p.id).append("'><button class='action-link danger' type='submit' title='Excluir produto'>").append(icon("trash")).append(" <span>Excluir</span></button></form></td></tr>");
            }
            body.append("</tbody></table></div>");
        }
        body.append("</section><section class='panel create-panel'><div class='panel-head'><div class='panel-heading-icon'>").append(icon("plus")).append("</div><div><h2>Novo produto</h2><p class='muted'>Adicione um item ao catálogo.</p></div></div>")
            .append("<form method='post' action='/produtos/criar' class='form-stack'>").append(hidden(session))
            .append("<label>Nome do produto <span class='required'>*</span><input name='nome' required maxlength='120' placeholder='Ex.: Martelo'></label>")
            .append("<label>Descrição<input name='descricao' maxlength='255' placeholder='Detalhes do produto'></label>")
            .append("<div class='form-grid'><label>Estoque inicial <span class='required'>*</span><input type='number' name='inicial' required min='0' value='0'></label>")
            .append("<label>Estoque mínimo <span class='required'>*</span><input type='number' name='minimo' required min='0' value='0'></label></div>")
            .append("<button class='button primary' type='submit'>").append(icon("plus")).append(" Cadastrar produto</button></form></section></div>");
        respond(exchange, 200, page("Produtos", session, "products", body.toString()));
    }

    private void createProduct(HttpExchange exchange, Session session, Map<String, String> form) throws IOException {
        try {
            store.createProduct(form.get("nome"), form.getOrDefault("descricao", ""),
                Logic.nonNegative(form.getOrDefault("inicial", ""), "Estoque inicial"),
                Logic.nonNegative(form.getOrDefault("minimo", ""), "Estoque mínimo"), session.user);
            redirect(exchange, "/produtos?ok=" + enc("Produto cadastrado com sucesso."));
        } catch (Exception e) { redirect(exchange, "/produtos?erro=" + enc(message(e))); }
    }

    private void editPage(HttpExchange exchange, Session session) throws Exception {
        long id;
        try { id = Long.parseLong(query(exchange).getOrDefault("id", "")); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Produto inválido."); }
        Store.Product product = store.products("").stream().filter(p -> p.id == id).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Produto não encontrado."));
        String body = "<div class='page-heading'><div><p class='eyebrow'>CATÁLOGO</p><h1>Editar produto</h1>" +
            "<p class='muted'>Altere os dados de " + esc(product.name) + ". O saldo muda apenas pelas movimentações.</p></div></div>" +
            "<section class='panel form-page'><form method='post' action='/produtos/editar' class='form-stack'>" + hidden(session) +
            "<input type='hidden' name='id' value='" + product.id + "'>" +
            "<label>Nome do produto<input name='nome' value='" + esc(product.name) + "' required maxlength='120'></label>" +
            "<label>Descrição<input name='descricao' value='" + esc(product.description) + "' maxlength='255'></label>" +
            "<label>Estoque mínimo<input type='number' name='minimo' value='" + product.minimum + "' required min='0'></label>" +
            "<div class='form-actions'><button class='button primary' type='submit'>" + icon("check") + " Salvar alterações</button><a class='button subtle' href='/produtos'>" + icon("arrow-left") + " Cancelar</a></div>" +
            "</form></section>";
        respond(exchange, 200, page("Editar produto", session, "products", body));
    }

    private void editProduct(HttpExchange exchange, Map<String, String> form) throws IOException {
        try {
            store.updateProduct(id(form), form.get("nome"), form.getOrDefault("descricao", ""),
                Logic.nonNegative(form.getOrDefault("minimo", ""), "Estoque mínimo"));
            redirect(exchange, "/produtos?ok=" + enc("Produto atualizado com sucesso."));
        } catch (Exception e) { redirect(exchange, "/produtos?erro=" + enc(message(e))); }
    }

    private void deleteProduct(HttpExchange exchange, Map<String, String> form) throws IOException {
        try {
            store.deleteProduct(id(form));
            redirect(exchange, "/produtos?ok=" + enc("Produto excluído. O histórico foi preservado."));
        } catch (Exception e) { redirect(exchange, "/produtos?erro=" + enc(message(e))); }
    }

    private void stock(HttpExchange exchange, Session session) throws Exception {
        List<Store.Product> products = store.products("");
        StringBuilder body = new StringBuilder();
        body.append("<div class='page-heading'><div><p class='eyebrow'>OPERAÇÕES</p><h1>Gestão de estoque</h1><p class='muted'>Registre entradas e saídas com rastreabilidade.</p></div><div class='heading-actions'><a class='button subtle' href='/historico'>")
            .append(icon("clock")).append(" Ver histórico</a><a class='button primary' href='#novo-item'>")
            .append(icon("plus")).append(" Receber item novo</a></div></div>")
            .append(flash(query(exchange))).append("<div class='grid-two stock-grid'><section class='panel'><div class='panel-head'><div class='panel-heading-icon'>")
            .append(icon("arrows")).append("</div><div><h2>Registrar movimentação</h2><p class='muted'>Selecione o produto e informe os dados.</p></div></div>")
            .append("<form method='post' action='/estoque/movimentar' class='form-stack'>").append(hidden(session))
            .append("<label>Produto<select name='produto_id' required>");
        if (products.isEmpty()) body.append("<option value=''>Cadastre um produto primeiro</option>");
        for (Store.Product p : products) body.append("<option value='").append(p.id).append("'>").append(esc(p.name)).append(" · saldo ").append(p.current).append("</option>");
        body.append("</select></label><div class='form-grid'><label>Tipo<select name='tipo'><option value='ENTRADA'>Entrada</option><option value='SAIDA'>Saída</option></select></label>")
            .append("<label>Quantidade<input type='number' name='quantidade' min='1' required></label></div>")
            .append("<label>Data (dd/MM/aaaa)<input name='data' required pattern='[0-9]{2}/[0-9]{2}/[0-9]{4}' value='")
            .append(Logic.DATE.format(LocalDate.now())).append("'></label>")
            .append("<button class='button primary' type='submit' ").append(products.isEmpty() ? "disabled" : "").append(">").append(icon("check")).append(" Registrar movimentação</button></form></section>")
            .append("<section class='panel'><div class='panel-head'><div class='panel-heading-icon'>").append(icon("package")).append("</div><div><h2>Saldo dos produtos</h2><p class='muted'>Ordenados alfabeticamente.</p></div><span class='count'>").append(products.size()).append(" itens</span></div>");
        if (products.isEmpty()) body.append("<div class='empty'>Nenhum produto cadastrado.</div>");
        else {
            body.append("<div class='table-wrap'><table><thead><tr><th>Produto</th><th>Saldo</th><th>Mínimo</th><th>Situação</th></tr></thead><tbody>");
            for (Store.Product p : products) {
                boolean low = Logic.belowMinimum(p.current, p.minimum);
                body.append("<tr><td>").append(esc(p.name)).append("</td><td><strong>").append(p.current).append("</strong></td><td>").append(p.minimum)
                    .append("</td><td><span class='badge ").append(low ? "low'>Abaixo do mínimo" : "ok'>Normal").append("</span></td></tr>");
            }
            body.append("</tbody></table></div>");
        }
        body.append("</section></div>")
            .append("<section class='panel new-entry-panel' id='novo-item'><div class='panel-head'><div class='panel-heading-icon'>")
            .append(icon("plus")).append("</div><div><h2>Entrada de item novo</h2><p class='muted'>Ferramenta recebida de fora que ainda não consta no catálogo. O cadastro e a entrada serão registrados juntos.</p></div></div>")
            .append("<form method='post' action='/estoque/novo-item' class='form-stack'>").append(hidden(session))
            .append("<div class='form-grid'><label>Nome da ferramenta <span class='required'>*</span><input name='nome' required maxlength='120' placeholder='Ex.: Furadeira'></label>")
            .append("<label>Descrição<input name='descricao' maxlength='255' placeholder='Modelo ou detalhes de identificação'></label></div>")
            .append("<div class='form-grid'><label>Quantidade recebida <span class='required'>*</span><input type='number' name='quantidade' required min='1' placeholder='Ex.: 5'></label>")
            .append("<label>Estoque mínimo <span class='required'>*</span><input type='number' name='minimo' required min='0' value='0'></label></div>")
            .append("<label class='date-field'>Data da entrada (dd/MM/aaaa) <span class='required'>*</span><input name='data' required pattern='[0-9]{2}/[0-9]{2}/[0-9]{4}' value='")
            .append(Logic.DATE.format(LocalDate.now())).append("'></label>")
            .append("<div class='new-entry-actions'><p class='muted'>O item aparecerá no catálogo, no saldo de estoque e no histórico de movimentações.</p><button class='button primary' type='submit'>")
            .append(icon("check")).append(" Cadastrar e dar entrada</button></div></form></section>");
        respond(exchange, 200, page("Gestão de estoque", session, "stock", body.toString()));
    }

    private void receiveNewItem(HttpExchange exchange, Session session, Map<String, String> form) throws IOException {
        try {
            int quantity = Logic.positive(form.getOrDefault("quantidade", ""), "Quantidade recebida");
            int minimum = Logic.nonNegative(form.getOrDefault("minimo", ""), "Estoque mínimo");
            LocalDate date = Logic.date(form.getOrDefault("data", ""));
            store.createProduct(form.get("nome"), form.getOrDefault("descricao", ""), quantity, minimum, date, session.user);
            redirect(exchange, "/estoque?ok=" + enc("Item novo cadastrado e entrada de " + quantity + " unidade(s) registrada no histórico."));
        } catch (Exception e) { redirect(exchange, "/estoque?erro=" + enc(message(e))); }
    }

    private void move(HttpExchange exchange, Session session, Map<String, String> form) throws IOException {
        try {
            long productId = Long.parseLong(form.getOrDefault("produto_id", ""));
            String type = form.getOrDefault("tipo", "");
            Store.Product p = store.move(productId, type, Logic.positive(form.getOrDefault("quantidade", ""), "Quantidade"),
                Logic.date(form.getOrDefault("data", "")), session.user);
            String result = "Movimentação registrada. Saldo de " + p.name + ": " + p.current + ".";
            String kind = "ok";
            if (type.equals("SAIDA") && Logic.belowMinimum(p.current, p.minimum)) {
                result += " ATENÇÃO: o produto está abaixo do estoque mínimo (" + p.minimum + ").";
                kind = "alerta";
            }
            redirect(exchange, "/estoque?" + kind + "=" + enc(result));
        } catch (Exception e) { redirect(exchange, "/estoque?erro=" + enc(message(e))); }
    }

    private void history(HttpExchange exchange, Session session) throws Exception {
        List<Store.Movement> movements = store.movements();
        String search = query(exchange).getOrDefault("busca", "").toLowerCase(java.util.Locale.ROOT);
        StringBuilder body = new StringBuilder();
        body.append("<div class='page-heading'><div><p class='eyebrow'>RASTREABILIDADE</p><h1>Histórico de movimentações</h1><p class='muted'>Cada operação registrada, com data e responsável.</p></div><a class='button primary' href='/estoque'>")
            .append(icon("plus")).append(" Nova movimentação</a></div>")
            .append("<section class='panel'><div class='panel-head'><div class='panel-heading-icon'>").append(icon("clock")).append("</div><div><h2>Registros</h2><p class='muted'>Consulte entradas e saídas.</p></div></div><form class='search-row' method='get' action='/historico'><span class='search-field'>")
            .append(icon("search")).append("<input name='busca' placeholder='Buscar produto ou responsável' value='")
            .append(esc(query(exchange).getOrDefault("busca", ""))).append("'></span><button class='button subtle'>Buscar</button><a class='text-link' href='/historico'>Limpar</a></form>")
            .append("<div class='table-wrap'><table><thead><tr><th>Produto</th><th>Tipo</th><th>Quantidade</th><th>Data</th><th>Responsável</th></tr></thead><tbody>");
        int count = 0;
        for (Store.Movement m : movements) {
            if (!m.product.toLowerCase(java.util.Locale.ROOT).contains(search) && !m.user.toLowerCase(java.util.Locale.ROOT).contains(search)) continue;
            count++;
            body.append("<tr><td><strong>").append(esc(m.product)).append("</strong></td><td><span class='badge ").append(m.type.equals("ENTRADA") ? "ok'>Entrada" : "neutral'>Saída")
                .append("</span></td><td>").append(m.quantity).append("</td><td>").append(Logic.DATE.format(m.date)).append("</td><td>").append(esc(m.user)).append("</td></tr>");
        }
        body.append("</tbody></table></div>");
        if (count == 0) body.append("<div class='empty'>Nenhuma movimentação encontrada.</div>");
        body.append("</section>");
        respond(exchange, 200, page("Histórico", session, "history", body.toString()));
    }

    private static String page(String title, Session session, String active, String body) {
        StringBuilder html = new StringBuilder("<!doctype html><html lang='pt-BR'><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1'>")
            .append("<title>").append(esc(title)).append(" · SAEP</title><link rel='stylesheet' href='/assets/style.css?v=20260924b'></head><body>");
        if (session != null) {
            html.append("<div class='app-layout'><aside class='sidebar'><a class='brand' href='/'><span class='brand-symbol'>")
                .append(icon("package")).append("</span><span>SAEP <small>CONTROLE DE ESTOQUE</small></span></a>")
                .append("<div class='nav-section-title'>ESPAÇO DE TRABALHO</div><nav class='sidebar-nav' aria-label='Navegação principal'>")
                .append(navLink(active, "home", "/", "Visão geral", "grid"))
                .append(navLink(active, "products", "/produtos", "Produtos", "package"))
                .append(navLink(active, "stock", "/estoque", "Gestão de estoque", "arrows"))
                .append(navLink(active, "history", "/historico", "Histórico", "clock"))
                .append("</nav><div class='sidebar-bottom'><div class='sidebar-user'><span class='user-avatar'>")
                .append(esc(session.user.name.substring(0, 1).toUpperCase(java.util.Locale.ROOT)))
                .append("</span><span class='user-info'><strong>").append(esc(session.user.name))
                .append("</strong><small>Usuário autorizado</small></span></div>")
                .append("<form method='post' action='/logout'>").append(hidden(session))
                .append("<button class='logout' type='submit'>").append(icon("logout")).append(" Sair do sistema</button></form></div></aside>")
                .append("<div class='workspace'><header class='topbar'><div><span class='topbar-prefix'>SAEP</span><span class='topbar-separator'>/</span><strong>")
                .append(esc(title)).append("</strong></div><span class='topbar-status'><span></span> Sistema operacional</span></header>")
                .append("<main class='content'>").append(body).append("</main></div></div>");
        } else html.append(body);
        html.append("</body></html>");
        return html.toString();
    }

    private static String navLink(String active, String id, String href, String label, String symbol) {
        return "<a class='nav-link" + (active.equals(id) ? " active" : "") + "' href='" + href + "'" +
            (active.equals(id) ? " aria-current='page'" : "") + ">" + icon(symbol) + "<span>" + label + "</span></a>";
    }

    private static String icon(String name) {
        String paths;
        switch (name) {
            case "package": paths = "<path d='m12 2 9 5-9 5-9-5 9-5Z'/><path d='M3 7v10l9 5 9-5V7M12 12v10M7.5 4.5l9 5'/>"; break;
            case "grid": paths = "<rect x='3' y='3' width='7' height='7' rx='1'/><rect x='14' y='3' width='7' height='7' rx='1'/><rect x='3' y='14' width='7' height='7' rx='1'/><rect x='14' y='14' width='7' height='7' rx='1'/>"; break;
            case "arrows": paths = "<path d='M7 7h14m-4-4 4 4-4 4M17 17H3m4-4-4 4 4 4'/>"; break;
            case "clock": paths = "<circle cx='12' cy='12' r='9'/><path d='M12 7v5l3 2'/>"; break;
            case "logout": paths = "<path d='M9 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h4M15 17l5-5-5-5M20 12H9'/>"; break;
            case "search": paths = "<circle cx='11' cy='11' r='7'/><path d='m16 16 5 5'/>"; break;
            case "plus": paths = "<path d='M12 5v14M5 12h14'/>"; break;
            case "alert": paths = "<path d='M10.3 3.6 2.4 18a2 2 0 0 0 1.8 3h15.6a2 2 0 0 0 1.8-3L13.7 3.6a2 2 0 0 0-3.4 0Z'/><path d='M12 9v4m0 4h.01'/>"; break;
            case "check": paths = "<path d='m4 12 5 5L20 6'/>"; break;
            case "edit": paths = "<path d='M12 4H5a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2h13a2 2 0 0 0 2-2v-7M16 4l4 4M10 14l9-9a2.1 2.1 0 0 1 3 3l-9 9-5 1 1-5Z'/>"; break;
            case "trash": paths = "<path d='M4 7h16M9 7V4h6v3m-9 0 1 14h10l1-14M10 11v6m4-6v6'/>"; break;
            case "shield": paths = "<path d='M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10Z'/><path d='m9 12 2 2 4-4'/>"; break;
            case "arrow-right": paths = "<path d='M4 12h16m-6-6 6 6-6 6'/>"; break;
            case "arrow-left": paths = "<path d='M20 12H4m6-6-6 6 6 6'/>"; break;
            case "info": paths = "<circle cx='12' cy='12' r='9'/><path d='M12 11v5m0-8h.01'/>"; break;
            default: throw new IllegalArgumentException("Ícone desconhecido: " + name);
        }
        return "<svg class='icon' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='1.8' stroke-linecap='round' stroke-linejoin='round' aria-hidden='true'>" + paths + "</svg>";
    }

    private static String hidden(Session s) { return "<input type='hidden' name='csrf' value='" + esc(s.csrf) + "'>"; }
    private static String flash(Map<String, String> query) {
        for (String kind : new String[]{"erro", "alerta", "ok"}) if (query.containsKey(kind))
            return "<div class='notice " + (kind.equals("erro") ? "error" : kind.equals("alerta") ? "warning" : "success") + "' role='alert'>" + icon(kind.equals("ok") ? "check" : "alert") + "<span>" + esc(query.get(kind)) + "</span></div>";
        return "";
    }
    private static String esc(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
    private static String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static String message(Exception e) { return e instanceof IllegalArgumentException ? e.getMessage() : "Falha no banco de dados. Tente novamente."; }
    private static long id(Map<String, String> form) { try { return Long.parseLong(form.getOrDefault("id", "")); } catch (NumberFormatException e) { throw new IllegalArgumentException("Produto inválido."); } }

    private Session session(HttpExchange exchange) {
        String token = cookie(exchange, "SAEP_SESSION");
        if (token == null) return null;
        Session session = sessions.get(token);
        if (session != null && System.currentTimeMillis() - session.createdAt > 8L * 60 * 60 * 1000) {
            sessions.remove(token); return null;
        }
        return session;
    }
    private static String cookie(HttpExchange exchange, String name) {
        String header = exchange.getRequestHeaders().getFirst("Cookie");
        if (header == null) return null;
        for (String part : header.split(";")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2 && kv[0].equals(name)) return kv[1];
        }
        return null;
    }
    private String token() {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private static Map<String, String> query(HttpExchange exchange) { return parse(exchange.getRequestURI().getRawQuery()); }
    private static Map<String, String> form(HttpExchange exchange) throws IOException {
        String type = exchange.getRequestHeaders().getFirst("Content-Type");
        if (type == null || !type.toLowerCase().startsWith("application/x-www-form-urlencoded")) throw new IllegalArgumentException("Tipo de formulário inválido.");
        byte[] data;
        try (InputStream stream = exchange.getRequestBody()) { data = stream.readNBytes(16_385); }
        if (data.length > 16_384) throw new IllegalArgumentException("Formulário muito grande.");
        return parse(new String(data, StandardCharsets.UTF_8));
    }
    private static Map<String, String> parse(String raw) {
        Map<String, String> values = new HashMap<>();
        if (raw == null || raw.isEmpty()) return values;
        for (String part : raw.split("&")) {
            String[] kv = part.split("=", 2);
            String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
            String value = URLDecoder.decode(kv.length == 2 ? kv[1] : "", StandardCharsets.UTF_8);
            values.putIfAbsent(key, value);
        }
        return values;
    }
    private static void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(303, -1);
    }
    private static void respond(HttpExchange exchange, int status, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("X-Frame-Options", "DENY");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
    private static void css(HttpExchange exchange) throws IOException {
        try (InputStream input = WebApp.class.getResourceAsStream("/style.css")) {
            if (input == null) { exchange.sendResponseHeaders(404, -1); return; }
            byte[] bytes = input.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "text/css; charset=UTF-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }
}
