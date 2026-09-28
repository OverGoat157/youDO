package ru.mirea.freelance.ui;

import ru.mirea.freelance.model.Order;
import ru.mirea.freelance.model.OrderCategory;
import ru.mirea.freelance.model.OrderStatus;
import ru.mirea.freelance.service.OrderService;
import ru.mirea.freelance.service.OrderSortField;
import ru.mirea.freelance.util.InputReader;
import ru.mirea.freelance.util.TablePrinter;

import java.util.List;

public class SearchMenu {

    private final OrderService orderService;
    private final InputReader in;

    public SearchMenu(OrderService orderService, InputReader in) {
        this.orderService = orderService;
        this.in = in;
    }

    public void search() {
        System.out.println("Поиск заказов:");
        System.out.println("1. По названию");
        System.out.println("2. По ID заказчика");
        System.out.println("3. По ID исполнителя");
        int choice = in.readInt("Выберите пункт: ");
        List<Order> result = switch (choice) {
            case 1 -> orderService.searchByTitle(in.readNonEmpty("Часть названия: "));
            case 2 -> orderService.searchByCustomer(in.readLong("ID заказчика: "));
            case 3 -> orderService.searchByFreelancer(in.readLong("ID исполнителя: "));
            default -> null;
        };
        print(result);
    }

    public void filter() {
        System.out.println("Фильтрация заказов:");
        System.out.println("1. По статусу");
        System.out.println("2. По категории");
        System.out.println("3. По бюджету");
        System.out.println("4. По дедлайну");
        int choice = in.readInt("Выберите пункт: ");
        List<Order> result = switch (choice) {
            case 1 -> orderService.filterByStatus(in.readEnum("Статус: ", OrderStatus.class));
            case 2 -> orderService.filterByCategory(in.readEnum("Категория: ", OrderCategory.class));
            case 3 -> orderService.filterByBudget(
                    in.readBigDecimal("Бюджет от: "),
                    in.readBigDecimal("Бюджет до: "));
            case 4 -> orderService.filterByDeadline(
                    in.readDate("Дедлайн с (гггг-мм-дд): "),
                    in.readDate("Дедлайн по (гггг-мм-дд): "));
            default -> null;
        };
        print(result);
    }

    public void sort() {
        OrderSortField field = in.readEnum("Сортировать по: ", OrderSortField.class);
        int direction = in.readInt("1 — по возрастанию, 2 — по убыванию: ");
        while (direction != 1 && direction != 2) {
            System.out.println("Ошибка: введите 1 или 2.");
            direction = in.readInt("1 — по возрастанию, 2 — по убыванию: ");
        }
        print(orderService.sortBy(field, direction == 1));
    }

    private void print(List<Order> result) {
        if (result == null) {
            System.out.println("Нет такого пункта");
            return;
        }
        TablePrinter.printOrders(result);
    }
}
