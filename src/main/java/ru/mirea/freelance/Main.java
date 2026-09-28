package ru.mirea.freelance;

import ru.mirea.freelance.exception.DatabaseException;
import ru.mirea.freelance.repository.JdbcOrderRepository;
import ru.mirea.freelance.repository.JdbcUserRepository;
import ru.mirea.freelance.repository.OrderRepository;
import ru.mirea.freelance.repository.UserRepository;
import ru.mirea.freelance.service.OrderService;
import ru.mirea.freelance.service.StatisticsService;
import ru.mirea.freelance.service.UserService;
import ru.mirea.freelance.ui.ConsoleMenu;
import ru.mirea.freelance.util.DatabaseManager;
import ru.mirea.freelance.util.DbInspector;
import ru.mirea.freelance.util.InputReader;

import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        try {
            DatabaseManager db = new DatabaseManager();
            UserRepository userRepo = new JdbcUserRepository(db);
            OrderRepository orderRepo = new JdbcOrderRepository(db);
            UserService userService = new UserService(userRepo, orderRepo);
            OrderService orderService = new OrderService(orderRepo, userRepo);
            StatisticsService statisticsService = new StatisticsService(userRepo, orderRepo);
            DbInspector dbInspector = new DbInspector(db);
            InputReader in = new InputReader(new Scanner(System.in));
            new ConsoleMenu(userService, orderService, statisticsService, dbInspector, in).run();
        } catch (DatabaseException e) {
            System.out.println("Не удалось подключиться к БД, проверьте db.properties: " + e.getMessage());
        }
    }
}
