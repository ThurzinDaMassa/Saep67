package br.saep.estoque;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class LogicTest {
    private static int checks;
    private static void check(boolean condition, String name) {
        checks++;
        if (!condition) throw new AssertionError(name);
    }
    private static void rejects(Runnable action, String name) {
        boolean rejected = false;
        try { action.run(); } catch (IllegalArgumentException e) { rejected = true; }
        check(rejected, name);
    }
    public static void main(String[] args) throws Exception {
        check(Logic.nonNegative("0", "saldo") == 0, "aceita zero");
        rejects(() -> Logic.nonNegative("-1", "saldo"), "rejeita negativo");
        rejects(() -> Logic.nonNegative("abc", "saldo"), "rejeita texto");
        check(Logic.positive("2", "quantidade") == 2, "aceita positivo");
        rejects(() -> Logic.positive("0", "quantidade"), "rejeita quantidade zero");
        check(Logic.date("24/09/2026").equals(LocalDate.of(2026, 9, 24)), "interpreta data");
        rejects(() -> Logic.date("31/02/2026"), "rejeita data inexistente");
        check(Logic.belowMinimum(4, 5), "abaixo do mínimo");
        check(!Logic.belowMinimum(5, 5), "igual ao mínimo");
        List<Store.Product> products = new ArrayList<>();
        products.add(new Store.Product(1, "Martelo", "", 0, 0));
        products.add(new Store.Product(2, "Alicate", "", 0, 0));
        products.add(new Store.Product(3, "Chave de fenda", "", 0, 0));
        Logic.sortProducts(products);
        check(products.get(0).name.equals("Alicate") && products.get(2).name.equals("Martelo"), "ordenação alfabética");
        Store store = new Store();
        String[] hashes = {
            "a729d0f3184b0e54a8f1498626cb5d31:69dc0dc880a933ba2fd97f9cbd6250bd1017f59f4dae7e41e8921d77c3eb9539",
            "4fd7309062ab5c4491b9e732f88a014b:62a2706aeefa05968a010f612f67d42e9c729db5bee46ee4198ed839c3504889",
            "d6b144fe26c08941acc2f20c01f330c2:239bca8cf96d7affec84070a058af87d8b77adf64491747bd8f61ab3ca4f47da"
        };
        for (String hash : hashes) check(store.verifyPassword("Saep@2026".toCharArray(), hash), "senha demonstrativa válida");
        check(!store.verifyPassword("errada".toCharArray(), hashes[0]), "senha incorreta recusada");
        System.out.println("PASSOU: " + checks + " verificações de lógica.");
    }
}
