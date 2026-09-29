package ru.mirea.freelance.ui;

import ru.mirea.freelance.service.OrderService;
import ru.mirea.freelance.service.StatisticsService;
import ru.mirea.freelance.service.UserService;
import ru.mirea.freelance.util.CsvExporter;
import ru.mirea.freelance.util.DbInspector;
import ru.mirea.freelance.util.ExcelExporter;
import ru.mirea.freelance.util.Exporter;
import ru.mirea.freelance.util.InputReader;
import ru.mirea.freelance.util.TablePrinter;

import java.nio.file.Path;
import java.util.List;

public class StatsMenu {

    private final UserService userService;
    private final OrderService orderService;
    private final StatisticsService statisticsService;
    private final DbInspector dbInspector;
    private final InputReader in;

    public StatsMenu(UserService userService, OrderService orderService, StatisticsService statisticsService,
                     DbInspector dbInspector, InputReader in) {
        this.userService = userService;
        this.orderService = orderService;
        this.statisticsService = statisticsService;
        this.dbInspector = dbInspector;
        this.in = in;
    }

    public void showStatistics() {
        System.out.println("Статистика");
        TablePrinter.printStats(statisticsService.collect());
    }

    public void export() {
        int choice = in.readInt("1 — Excel, 2 — CSV: ");
        while (choice != 1 && choice != 2) {
            System.out.println("Ошибка: введите 1 или 2.");
            choice = in.readInt("1 — Excel, 2 — CSV: ");
        }
        Exporter exporter = (choice == 1) ? new ExcelExporter() : new CsvExporter();
        List<Path> files = exporter.export(userService.findAll(), orderService.findAll(), Path.of("export"));
        files.forEach(f -> System.out.println("Сохранено: " + f.toAbsolutePath()));
    }

    public void printTables() {
        dbInspector.printAllTables();
    }
}
