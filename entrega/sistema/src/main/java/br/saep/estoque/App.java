package br.saep.estoque;

import java.awt.*;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;

public final class App {
    private final Store store = new Store();
    private final JFrame frame = new JFrame("SAEP - Gestão de Estoque");
    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);
    private final JTabbedPane tabs = new JTabbedPane();
    private final JTextField login = new JTextField(18);
    private final JPasswordField password = new JPasswordField(18);
    private final JTextField search = new JTextField(22);
    private final DefaultTableModel productsModel = model("ID", "Produto", "Descrição", "Saldo", "Mínimo", "Situação");
    private final DefaultTableModel historyModel = model("ID", "Produto", "Tipo", "Quantidade", "Data", "Responsável");
    private final JTable productsTable = new JTable(productsModel);
    private final JTable historyTable = new JTable(historyModel);
    private final JComboBox<ProductItem> productSelect = new JComboBox<>();
    private final JComboBox<String> typeSelect = new JComboBox<>(new String[]{"ENTRADA", "SAIDA"});
    private final JTextField quantity = new JTextField(10);
    private final JTextField moveDate = new JTextField(Logic.DATE.format(LocalDate.now()), 10);
    private final JLabel welcome = new JLabel();
    private final JLabel summary = new JLabel();
    private Store.User currentUser;
    private List<Store.Product> shownProducts = new ArrayList<>();

    private App() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(800, 560));
        root.add(loginPanel(), "login");
        root.add(mainPanel(), "main");
        frame.setContentPane(root);
        frame.setSize(940, 640);
        frame.setLocationRelativeTo(null);
        cards.show(root, "login");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new App().frame.setVisible(true));
    }

    private JPanel loginPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        JPanel box = new JPanel(new GridLayout(0, 2, 12, 12));
        box.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder("Acesso ao sistema"), BorderFactory.createEmptyBorder(22, 22, 22, 22)));
        box.add(new JLabel("Usuário:")); box.add(login);
        box.add(new JLabel("Senha:")); box.add(password);
        JButton enter = button("Entrar", () -> doLogin());
        box.add(new JLabel("")); box.add(enter);
        password.addActionListener(e -> doLogin());
        panel.add(box);
        return panel;
    }

    private JPanel mainPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        JPanel header = new JPanel(new BorderLayout());
        welcome.setFont(welcome.getFont().deriveFont(Font.BOLD, 18f));
        header.add(welcome, BorderLayout.WEST);
        header.add(button("Logout", this::logout), BorderLayout.EAST);
        panel.add(header, BorderLayout.NORTH);
        tabs.addTab("Início", homePanel());
        tabs.addTab("Cadastro de produtos", productsPanel());
        tabs.addTab("Gestão de estoque", stockPanel());
        tabs.addTab("Histórico", historyPanel());
        tabs.addChangeListener(e -> { if (currentUser != null) safe(this::refreshAll); });
        panel.add(tabs, BorderLayout.CENTER);
        return panel;
    }

    private JPanel homePanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        summary.setFont(summary.getFont().deriveFont(17f));
        panel.add(summary, BorderLayout.NORTH);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.add(button("Cadastro de produtos", () -> tabs.setSelectedIndex(1)));
        actions.add(button("Gestão de estoque", () -> tabs.setSelectedIndex(2)));
        actions.add(button("Histórico", () -> tabs.setSelectedIndex(3)));
        panel.add(actions, BorderLayout.CENTER);
        return panel;
    }

    private JPanel productsPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Pesquisar:")); top.add(search);
        top.add(button("Buscar", () -> safe(this::refreshProducts)));
        top.add(button("Mostrar todos", () -> { search.setText(""); safe(this::refreshProducts); }));
        panel.add(top, BorderLayout.NORTH);
        productsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        productsTable.setAutoCreateRowSorter(false);
        productsTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, selected, focus, row, column);
                c.setForeground(selected ? table.getSelectionForeground() :
                        "Abaixo do mínimo".equals(table.getValueAt(row, 5)) ? new Color(175, 34, 34) : table.getForeground());
                return c;
            }
        });
        panel.add(new JScrollPane(productsTable), BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.add(button("Cadastrar", () -> productDialog(null)));
        actions.add(button("Editar", () -> { Store.Product p = selectedProduct(); if (p != null) productDialog(p); }));
        actions.add(button("Excluir", this::deleteSelected));
        actions.add(button("Voltar ao início", () -> tabs.setSelectedIndex(0)));
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel stockPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        JPanel form = new JPanel(new GridLayout(0, 2, 10, 14));
        form.setBorder(BorderFactory.createEmptyBorder(18, 12, 18, 12));
        form.add(new JLabel("Produto (ordem alfabética):")); form.add(productSelect);
        form.add(new JLabel("Tipo:")); form.add(typeSelect);
        form.add(new JLabel("Quantidade:")); form.add(quantity);
        form.add(new JLabel("Data (dd/MM/aaaa):")); form.add(moveDate);
        panel.add(form, BorderLayout.NORTH);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.add(button("Registrar movimentação", this::registerMove));
        actions.add(button("Voltar ao início", () -> tabs.setSelectedIndex(0)));
        panel.add(actions, BorderLayout.CENTER);
        return panel;
    }

    private JPanel historyPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.add(new JScrollPane(historyTable), BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.add(button("Atualizar", () -> safe(this::refreshHistory)));
        actions.add(button("Voltar ao início", () -> tabs.setSelectedIndex(0)));
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private void doLogin() {
        char[] pass = password.getPassword();
        try {
            currentUser = store.authenticate(login.getText(), pass);
            password.setText("");
            welcome.setText("Sistema de Controle de Estoque  |  Usuário: " + currentUser.name);
            refreshAll();
            tabs.setSelectedIndex(0);
            cards.show(root, "main");
        } catch (Exception e) { error(e); }
        finally { Arrays.fill(pass, '\0'); }
    }

    private void logout() {
        currentUser = null;
        login.setText(""); password.setText("");
        productsModel.setRowCount(0); historyModel.setRowCount(0);
        cards.show(root, "login");
    }

    private void refreshAll() throws SQLException {
        refreshProducts(); refreshSelect(); refreshHistory();
        List<Store.Product> all = store.products("");
        long low = all.stream().filter(p -> Logic.belowMinimum(p.current, p.minimum)).count();
        summary.setText("Produtos ativos: " + all.size() + "     |     Abaixo do mínimo: " + low);
    }

    private void refreshProducts() throws SQLException {
        shownProducts = store.products(search.getText());
        productsModel.setRowCount(0);
        for (Store.Product p : shownProducts) productsModel.addRow(new Object[]{p.id, p.name, p.description, p.current, p.minimum,
                Logic.belowMinimum(p.current, p.minimum) ? "Abaixo do mínimo" : "Normal"});
    }

    private void refreshSelect() throws SQLException {
        productSelect.removeAllItems();
        for (Store.Product p : store.products("")) productSelect.addItem(new ProductItem(p));
    }

    private void refreshHistory() throws SQLException {
        historyModel.setRowCount(0);
        for (Store.Movement m : store.movements()) historyModel.addRow(new Object[]{m.id, m.product, m.type, m.quantity, Logic.DATE.format(m.date), m.user});
    }

    private Store.Product selectedProduct() {
        int row = productsTable.getSelectedRow();
        if (row < 0) { JOptionPane.showMessageDialog(frame, "Selecione um produto na tabela."); return null; }
        return shownProducts.get(row);
    }

    private void productDialog(Store.Product old) {
        JTextField name = new JTextField(old == null ? "" : old.name);
        JTextField description = new JTextField(old == null ? "" : old.description);
        JTextField initial = new JTextField("0");
        JTextField minimum = new JTextField(old == null ? "0" : String.valueOf(old.minimum));
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Nome*:")); form.add(name);
        form.add(new JLabel("Descrição:")); form.add(description);
        if (old == null) { form.add(new JLabel("Estoque inicial*:")); form.add(initial); }
        form.add(new JLabel("Estoque mínimo*:")); form.add(minimum);
        int result = JOptionPane.showConfirmDialog(frame, form, old == null ? "Cadastrar produto" : "Editar produto", JOptionPane.OK_CANCEL_OPTION);
        if (result != JOptionPane.OK_OPTION) return;
        safe(() -> {
            int min = Logic.nonNegative(minimum.getText(), "Estoque mínimo");
            if (old == null) store.createProduct(name.getText(), description.getText(), Logic.nonNegative(initial.getText(), "Estoque inicial"), min, currentUser);
            else store.updateProduct(old.id, name.getText(), description.getText(), min);
            refreshAll();
            JOptionPane.showMessageDialog(frame, "Produto salvo com sucesso.");
        });
    }

    private void deleteSelected() {
        Store.Product p = selectedProduct();
        if (p == null) return;
        if (JOptionPane.showConfirmDialog(frame, "Excluir " + p.name + "? O histórico será preservado.", "Confirmar exclusão", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        safe(() -> { store.deleteProduct(p.id); refreshAll(); JOptionPane.showMessageDialog(frame, "Produto excluído."); });
    }

    private void registerMove() {
        ProductItem selected = (ProductItem)productSelect.getSelectedItem();
        if (selected == null) { JOptionPane.showMessageDialog(frame, "Cadastre um produto antes de movimentar."); return; }
        safe(() -> {
            String type = (String)typeSelect.getSelectedItem();
            Store.Product updated = store.move(selected.product.id, type, Logic.positive(quantity.getText(), "Quantidade"), Logic.date(moveDate.getText()), currentUser);
            refreshAll();
            quantity.setText("");
            String message = "Movimentação registrada. Saldo atual: " + updated.current + ".";
            if ("SAIDA".equals(type) && Logic.belowMinimum(updated.current, updated.minimum))
                message += "\nATENÇÃO: " + updated.name + " está abaixo do estoque mínimo (" + updated.minimum + ").";
            JOptionPane.showMessageDialog(frame, message);
        });
    }

    private interface Task { void run() throws Exception; }
    private void safe(Task task) { try { task.run(); } catch (Exception e) { error(e); } }
    private void error(Exception e) { JOptionPane.showMessageDialog(frame, e.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE); }
    private JButton button(String label, Runnable action) { JButton b = new JButton(label); b.addActionListener(e -> action.run()); return b; }
    private static DefaultTableModel model(String... columns) {
        return new DefaultTableModel(columns, 0) { @Override public boolean isCellEditable(int row, int column) { return false; } };
    }
    private static final class ProductItem {
        final Store.Product product;
        ProductItem(Store.Product product) { this.product = product; }
        @Override public String toString() { return product.name + " (saldo: " + product.current + ")"; }
    }
}
