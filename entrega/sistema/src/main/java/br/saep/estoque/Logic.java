package br.saep.estoque;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Comparator;
import java.util.List;

public final class Logic {
    private Logic() {}
    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);

    public static int nonNegative(String value, String field) {
        try {
            int number = Integer.parseInt(value.trim());
            if (number < 0) throw new NumberFormatException();
            return number;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " deve ser um número inteiro maior ou igual a zero.");
        }
    }

    public static int positive(String value, String field) {
        int number = nonNegative(value, field);
        if (number == 0) throw new IllegalArgumentException(field + " deve ser maior que zero.");
        return number;
    }

    public static LocalDate date(String value) {
        try { return LocalDate.parse(value.trim(), DATE); }
        catch (Exception e) { throw new IllegalArgumentException("Data inválida. Use dd/MM/aaaa."); }
    }

    public static boolean belowMinimum(int current, int minimum) { return current < minimum; }

    public static void sortProducts(List<Store.Product> products) {
        products.sort(Comparator.comparing(p -> p.name.toLowerCase(java.util.Locale.ROOT)));
    }
}
