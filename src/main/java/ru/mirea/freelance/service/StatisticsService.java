package ru.mirea.freelance.service;

import ru.mirea.freelance.model.Order;
import ru.mirea.freelance.model.OrderStatus;
import ru.mirea.freelance.model.User;
import ru.mirea.freelance.model.UserRole;
import ru.mirea.freelance.repository.OrderRepository;
import ru.mirea.freelance.repository.UserRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Статистика по пользователям и заказам.
 * Данные читаются из базы один раз, дальше всё считается через Stream API.
 */
public class StatisticsService {

    // Репозитории передаёт тот, кто создаёт сервис (меню); сам сервис SQL не пишет
    private final UserRepository users;
    private final OrderRepository orders;

    public StatisticsService(UserRepository users, OrderRepository orders) {
        this.users = users;
        this.orders = orders;
    }

    /** Возвращает показатели: ключ — название, значение — число или текст. Порядок ключей = порядок вывода. */
    public Map<String, Object> collect() {
        // В базу идём один раз, дальше всё считаем по этим спискам в памяти
        List<User> allUsers = users.findAll();
        List<Order> allOrders = orders.findAll();
        LocalDate today = LocalDate.now();

        // LinkedHashMap помнит порядок добавления — меню напечатает показатели в том же порядке
        Map<String, Object> stats = new LinkedHashMap<>();

        stats.put("Всего пользователей", allUsers.size());
        // filter оставляет пользователей с нужной ролью, count считает, сколько их осталось
        stats.put("Заказчиков", allUsers.stream().filter(u -> u.getRole() == UserRole.CUSTOMER).count());
        stats.put("Фрилансеров", allUsers.stream().filter(u -> u.getRole() == UserRole.FREELANCER).count());
        stats.put("Всего заказов", allOrders.size());

        // groupingBy не создаёт ключ для статуса без заказов, поэтому идём по всем статусам и подставляем 0
        Map<OrderStatus, Long> byStatus = allOrders.stream()
                .collect(Collectors.groupingBy(Order::getStatus, Collectors.counting()));
        for (OrderStatus status : OrderStatus.values()) {
            stats.put("Заказов " + status, byStatus.getOrDefault(status, 0L));
        }

        // average() есть только у потока double; если заказов нет, orElse подставит 0
        double averageBudget = allOrders.stream()
                .mapToDouble(o -> o.getBudget().doubleValue())
                .average()
                .orElse(0);
        // Округляем до копеек: 2 знака после запятой, HALF_UP — по школьному правилу (0.5 вверх)
        stats.put("Средний бюджет", BigDecimal.valueOf(averageBudget).setScale(2, RoundingMode.HALF_UP));

        // Деньги складываем в BigDecimal: в double копейки теряются на округлении
        BigDecimal activeBudget = allOrders.stream()
                .filter(o -> !o.getStatus().isFinal())
                .map(Order::getBudget)
                .reduce(BigDecimal.ZERO, BigDecimal::add);   // начинаем с нуля и прибавляем каждый бюджет
        stats.put("Сумма бюджетов активных заказов", activeBudget);

        // Просрочен: срок прошёл, а заказ ещё не завершён и не отменён
        stats.put("Просроченных заказов", allOrders.stream()
                .filter(o -> o.getDeadline().isBefore(today) && !o.getStatus().isFinal())
                .count());

        // Считаем заказы по категориям и берём категорию с наибольшим числом; пустая база → «—»
        String popularCategory = allOrders.stream()
                .collect(Collectors.groupingBy(Order::getCategory, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> e.getKey() + " (" + e.getValue() + ")")
                .orElse("—");
        stats.put("Самая популярная категория", popularCategory);

        return stats;
    }
}
