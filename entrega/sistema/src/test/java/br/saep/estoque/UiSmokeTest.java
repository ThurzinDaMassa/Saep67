package br.saep.estoque;

import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

/** Verifica o fluxo básico dos componentes Swing com o banco local ativo. */
public final class UiSmokeTest {
    private static int checks;
    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }
    @SuppressWarnings("unchecked")
    private static <T> T field(App app, String name) throws Exception {
        Field f = App.class.getDeclaredField(name);
        f.setAccessible(true);
        return (T)f.get(app);
    }
    private static JButton findButton(Component root, String label) {
        if (root instanceof JButton && ((JButton)root).getText().equals(label)) return (JButton)root;
        if (root instanceof Container) for (Component child : ((Container)root).getComponents()) {
            JButton found = findButton(child, label);
            if (found != null) return found;
        }
        return null;
    }
    public static void main(String[] args) throws Exception {
        final Throwable[] failure = new Throwable[1];
        SwingUtilities.invokeAndWait(() -> {
            JFrame frame = null;
            try {
                Constructor<App> ctor = App.class.getDeclaredConstructor();
                ctor.setAccessible(true);
                App app = ctor.newInstance();
                frame = field(app, "frame");
                JPanel root = field(app, "root");
                JTextField login = field(app, "login");
                JPasswordField password = field(app, "password");
                login.setText("administrador");
                password.setText("Saep@2026");
                JButton enter = findButton(root, "Entrar");
                check(enter != null, "botão Entrar presente");
                enter.doClick();
                Store.User logged = field(app, "currentUser");
                check(logged != null && logged.id == 1, "login pela interface");
                check(root.getComponent(1).isVisible(), "tela principal visível");
                DefaultTableModel products = field(app, "productsModel");
                check(products.getRowCount() >= 3, "produtos carregados na tabela");
                check("Alicate".equals(products.getValueAt(0, 1)), "produtos em ordem alfabética");
                JTabbedPane tabs = field(app, "tabs");
                check(tabs.getTabCount() == 4, "abas principais disponíveis");
                tabs.setSelectedIndex(2);
                JComboBox<?> select = field(app, "productSelect");
                check(select.getItemCount() >= 3, "produtos disponíveis na gestão de estoque");
                tabs.setSelectedIndex(3);
                DefaultTableModel history = field(app, "historyModel");
                check(history.getRowCount() >= 3, "histórico carregado na tabela");
                boolean hasResponsible = false;
                for (int row = 0; row < history.getRowCount(); row++)
                    if ("Almoxarife".equals(history.getValueAt(row, 5))) hasResponsible = true;
                check(hasResponsible, "responsável aparece no histórico");
                JButton logout = findButton(root, "Logout");
                check(logout != null, "botão Logout presente");
                logout.doClick();
                check(field(app, "currentUser") == null, "sessão encerrada");
                check(root.getComponent(0).isVisible(), "retorno à tela de login");
            } catch (Throwable e) { failure[0] = e; }
            finally { if (frame != null) frame.dispose(); }
        });
        if (failure[0] != null) throw new AssertionError("Falha no teste da interface", failure[0]);
        System.out.println("PASSOU: " + checks + " verificações da interface Swing.");
    }
}
