package ru.mirea.freelance.ui;

import ru.mirea.freelance.exception.BusinessException;
import ru.mirea.freelance.exception.DatabaseException;
import ru.mirea.freelance.service.OrderService;
import ru.mirea.freelance.service.StatisticsService;
import ru.mirea.freelance.service.UserService;
import ru.mirea.freelance.util.DbInspector;
import ru.mirea.freelance.util.InputReader;

public class ConsoleMenu {

    interface Submenu {
        void printOptions();
        void handle(int choice);
    }

    private final InputReader in;
    private final UserMenu userMenu;
    private final OrderMenu orderMenu;
    private final SearchMenu searchMenu;
    private final StatsMenu statsMenu;

    public ConsoleMenu(UserService userService, OrderService orderService, StatisticsService statisticsService,
                       DbInspector dbInspector, InputReader in) {
        this.in = in;
        this.userMenu = new UserMenu(userService, in);
        this.orderMenu = new OrderMenu(orderService, userService, in);
        this.searchMenu = new SearchMenu(orderService, in);
        this.statsMenu = new StatsMenu(userService, orderService, statisticsService, dbInspector, in);
    }

    public void run() {
        while (true) {
            System.out.println();
            System.out.println("БИРЖА ФРИЛАНСА youDO");
            System.out.println("1. Пользователи");
            System.out.println("2. Заказы");
            System.out.println("3. Поиск заказов");
            System.out.println("4. Фильтрация заказов");
            System.out.println("5. Сортировка заказов");
            System.out.println("6. Статистика");
            System.out.println("7. Экспорт данных");
            System.out.println("8. Вывести таблицы базы данных");
            System.out.println("0. Выход");
            int choice = in.readInt("Выберите пункт: ");
            if (choice == 0) {
                System.out.println("До свидания!");
                return;
            }
            switch (choice) {
                case 1 -> runSubmenu(userMenu);
                case 2 -> runSubmenu(orderMenu);
                case 3 -> execute(searchMenu::search);
                case 4 -> execute(searchMenu::filter);
                case 5 -> execute(searchMenu::sort);
                case 6 -> execute(statsMenu::showStatistics);
                case 7 -> execute(statsMenu::export);
                case 8 -> execute(statsMenu::printTables);
                default -> System.out.println("Нет такого пункта");
            }
        }
    }

    private void runSubmenu(Submenu submenu) {
        while (true) {
            System.out.println();
            submenu.printOptions();
            System.out.println("0. Назад");
            int choice = in.readInt("Выберите пункт: ");
            if (choice == 0) {
                return;
            }
            execute(() -> submenu.handle(choice));
        }
    }

    private void execute(Runnable action) {
        try {
            System.out.println();
            action.run();
        } catch (BusinessException | DatabaseException e) {
            System.out.println("Ошибка: " + e.getMessage());
        }
    }
}
