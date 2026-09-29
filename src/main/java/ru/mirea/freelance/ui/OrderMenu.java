package ru.mirea.freelance.ui;

import ru.mirea.freelance.model.Order;
import ru.mirea.freelance.model.OrderCategory;
import ru.mirea.freelance.model.OrderStatus;
import ru.mirea.freelance.model.UserRole;
import ru.mirea.freelance.service.OrderService;
import ru.mirea.freelance.service.UserService;
import ru.mirea.freelance.util.InputReader;
import ru.mirea.freelance.util.TablePrinter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

public class OrderMenu implements ConsoleMenu.Submenu {

    private final OrderService orderService;
    private final UserService userService;
    private final InputReader in;

    public OrderMenu(OrderService orderService, UserService userService, InputReader in) {
        this.orderService = orderService;
        this.userService = userService;
        this.in = in;
    }

    @Override
    public void printOptions() {
        System.out.println("Заказы");
        System.out.println("1. Список заказов");
        System.out.println("2. Найти по ID");
        System.out.println("3. Создать заказ");
        System.out.println("4. Изменить заказ");
        System.out.println("5. Удалить заказ");
        System.out.println("6. Назначить исполнителя");
        System.out.println("7. Сменить статус");
    }

    @Override
    public void handle(int choice) {
        switch (choice) {
            case 1 -> TablePrinter.printOrders(orderService.findAll());
            case 2 -> findById();
            case 3 -> create();
            case 4 -> update();
            case 5 -> delete();
            case 6 -> assignFreelancer();
            case 7 -> changeStatus();
            default -> System.out.println("Нет такого пункта");
        }
    }

    private void findById() {
        long id = in.readLong("ID заказа: ");
        printOrder(orderService.getById(id));
    }

    private void create() {
        System.out.println("Заказчики:");
        printUsersWithRole(UserRole.CUSTOMER);
        long customerId = in.readLong("ID заказчика: ");
        String title = in.readNonEmpty("Название: ");
        String description = in.readOptional("Описание (Enter — без описания): ");
        OrderCategory category = in.readEnum("Категория: ", OrderCategory.class);
        BigDecimal budget = in.readBigDecimal("Бюджет: ");
        LocalDate deadline = in.readDate("Дедлайн (гггг-мм-дд): ");
        Order order = orderService.create(title, description, category, budget, deadline, customerId);
        System.out.println("Заказ создан, ID = " + order.getId());
    }

    private void update() {
        long id = in.readLong("ID заказа: ");
        Order current = orderService.getById(id);
        System.out.println("Текущие данные:");
        printOrder(current);
        String title = in.readNonEmpty("Новое название: ");
        String description = in.readOptional("Новое описание (Enter — без описания): ");
        OrderCategory category = in.readEnum("Новая категория: ", OrderCategory.class);
        BigDecimal budget = in.readBigDecimal("Новый бюджет: ");
        LocalDate deadline = in.readDate("Новый дедлайн (гггг-мм-дд): ");
        orderService.update(id, title, description, category, budget, deadline);
        System.out.println("Заказ изменён");
    }

    private void delete() {
        long id = in.readLong("ID заказа: ");
        orderService.delete(id);
        System.out.println("Заказ удалён");
    }

    private void assignFreelancer() {
        long orderId = in.readLong("ID заказа: ");
        System.out.println("Фрилансеры:");
        printUsersWithRole(UserRole.FREELANCER);
        long freelancerId = in.readLong("ID фрилансера: ");
        printOrder(orderService.assignFreelancer(orderId, freelancerId));
        System.out.println("Исполнитель назначен");
    }

    private void changeStatus() {
        long id = in.readLong("ID заказа: ");
        Order order = orderService.getById(id);
        printOrder(order);
        OrderStatus current = order.getStatus();
        List<OrderStatus> allowed = Arrays.stream(OrderStatus.values())
                .filter(current::canTransitionTo)
                .toList();
        if (allowed.isEmpty()) {
            System.out.println("Заказ в финальном статусе, переходы невозможны");
            return;
        }
        System.out.println("Текущий статус: " + current + ", можно перевести в: " + allowed);
        OrderStatus next = in.readEnum("Новый статус: ", OrderStatus.class);
        printOrder(orderService.changeStatus(id, next));
        System.out.println("Статус изменён");
    }

    private void printUsersWithRole(UserRole role) {
        TablePrinter.printUsers(userService.findAll().stream()
                .filter(u -> u.getRole() == role)
                .toList());
    }

    private void printOrder(Order order) {
        TablePrinter.printOrders(List.of(order));
        if (order.getDescription() != null) {
            System.out.println("Описание: " + order.getDescription());
        }
    }
}
