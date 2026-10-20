package ru.mirea.freelance.util;

import ru.mirea.freelance.exception.BusinessException;
import ru.mirea.freelance.model.Order;
import ru.mirea.freelance.model.User;

import java.io.BufferedWriter;
import java.io.IOException;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Экспорт текущего набора заказов (того, что видно в таблице) в один CSV-файл.
 * В отличие от CsvExporter из КР №1 получает полный путь файла, а не папку.
 */
public class OrderCsvExporter {

    private static final String SEPARATOR = ";";
    private static final String[] HEADERS = {
            "Название", "Описание", "Категория", "Бюджет", "Дедлайн",
            "Статус", "Заказчик", "Исполнитель", "Создан"
    };
    // Один формат дат на весь файл; русский Excel распознаёт его как дату
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    /** Записывает заказы в file в том порядке, в каком они переданы. Пустой список — файл только с заголовками. */
    public void export(List<Order> orders, List<User> users, Path file) {
        // id → «Имя (email)»: в файле показываем людей, а не внутренние id
        Map<Long, String> names = new HashMap<>();
        for (User user : users) {
            names.put(user.getId(), user.getName() + " (" + user.getEmail() + ")");
        }

        List<String> lines = new ArrayList<>();
        lines.add(line(List.of(HEADERS)));
        for (Order o : orders) {
            List<String> values = new ArrayList<>();
            values.add(o.getTitle());
            values.add(o.getDescription());
            values.add(o.getCategory().toString());
            // Деньги — ровно два знака, дробная часть через запятую для русского Excel
            values.add(o.getBudget().setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ','));
            values.add(o.getDeadline().format(DATE));
            values.add(o.getStatus().toString());
            values.add(userName(names, o.getCustomerId()));
            values.add(userName(names, o.getFreelancerId()));
            values.add(o.getCreatedAt() == null ? null : o.getCreatedAt().format(DATE_TIME));
            lines.add(line(values));
        }

        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                // BOM: по нему Excel понимает, что файл в UTF-8, иначе кириллица станет кракозябрами
                writer.write('﻿');
                for (String line : lines) {
                    writer.write(line);
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            throw new BusinessException("Не удалось сохранить файл " + file + ": " + e.getMessage());
        }
    }

    /** Исполнителя может не быть (null) — тогда пустая ячейка. Пользователя нет в списке — показываем id. */
    private String userName(Map<Long, String> names, Long id) {
        if (id == null) {
            return null;
        }
        return names.getOrDefault(id, "id " + id);
    }

    private String line(List<String> values) {
        List<String> escaped = new ArrayList<>();
        for (String value : values) {
            escaped.add(escape(value));
        }
        return String.join(SEPARATOR, escaped);
    }

    /** null — пустое поле. Значение с «;», кавычкой или переводом строки — в кавычки, внутренние кавычки удваиваем. */
    private String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(SEPARATOR) || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
