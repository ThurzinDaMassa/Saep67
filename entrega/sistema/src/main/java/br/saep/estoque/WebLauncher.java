package br.saep.estoque;

import java.awt.Desktop;
import java.net.URI;

/** Entrada do executável Windows: inicia o servidor e abre o site no navegador. */
public final class WebLauncher {
    private WebLauncher() {}

    public static void main(String[] args) throws Exception {
        WebApp.main(args);
        String host = System.getenv().getOrDefault("SAEP_WEB_HOST", "127.0.0.1");
        String port = System.getenv().getOrDefault("SAEP_WEB_PORT", "8080");
        if ("1".equals(System.getenv("SAEP_NO_BROWSER"))) return;
        try {
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI.create("http://" + host + ":" + port));
        } catch (Exception e) {
            System.out.println("Abra o endereço acima no navegador.");
        }
    }
}
