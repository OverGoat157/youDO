package ru.mirea.freelance.util;

import ru.mirea.freelance.model.Order;
import ru.mirea.freelance.model.User;

import java.util.List;
import java.util.Map;

public class TablePrinter {

    private static final String USER_FORMAT = "%-4s | %-20s | %-25s | %-10s | %-7s | %-10s%n";
    private static final String ORDER_FORMAT = "%-4s | %-25s | %-11s | %-10s | %-10s | %-11s | %-8s | %-11s%n";

    public static void printUsers(List<User> users) {
        if (users.isEmpty()) {
            System.out.println("Записей нет");
            return;
        }
        String header = String.format(USER_FORMAT, "ID", "Имя", "Email", "Роль", "Рейтинг", "Регистрация");
        System.out.print(header);
        System.out.println("-".repeat(header.length() - 1));
        for (User u : users) {
            System.out.printf(USER_FORMAT,
                    u.getId(),
                    cut(u.getName(), 20),
                    cut(u.getEmail(), 25),
                    u.getRole(),
                    String.format("%.1f", u.getRating()),
                    u.getRegisteredAt());
        }
    }

    public static void printOrders(List<Order> orders) {
        if (orders.isEmpty()) {
            System.out.println("Записей нет");
            return;
        }
        String header = String.format(ORDER_FORMAT, "ID", "Название", "Категория", "Бюджет", "Дедлайн",
                "Статус", "Заказчик", "Исполнитель");
        System.out.print(header);
        System.out.println("-".repeat(header.length() - 1));
        for (Order o : orders) {
            System.out.printf(ORDER_FORMAT,
                    o.getId(),
                    cut(o.getTitle(), 25),
                    o.getCategory(),
                    cut(o.getBudget().toPlainString(), 10),
                    o.getDeadline(),
                    o.getStatus(),
                    o.getCustomerId(),
                    o.getFreelancerId() == null ? "—" : o.getFreelancerId());
        }
    }

    public static void printStats(Map<String, Object> stats) {
        if (stats.isEmpty()) {
            System.out.println("Записей нет");
            return;
        }
        for (Map.Entry<String, Object> e : stats.entrySet()) {
            System.out.println(e.getKey() + ": " + e.getValue());
        }
    }

    private static String cut(String value, int width) {
        if (value == null) {
            return "";
        }
        return value.length() <= width ? value : value.substring(0, width - 1) + "…";
    }
}
