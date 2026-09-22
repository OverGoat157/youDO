package ru.mirea.freelance.util;

import ru.mirea.freelance.model.Order;
import ru.mirea.freelance.model.User;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Экспорт в CSV: два файла users.csv и orders.csv, разделитель «;», кодировка UTF-8 с BOM.
 */
public class CsvExporter implements Exporter {

    private static final String SEPARATOR = ";";

    @Override
    public List<Path> export(List<User> users, List<Order> orders, Path directory) {
        List<String> userLines = new ArrayList<>();
        userLines.add(line("ID", "Имя", "Email", "Роль", "Рейтинг", "Дата регистрации"));
        for (User u : users) {
            userLines.add(line(u.getId(), u.getName(), u.getEmail(), u.getRole(), u.getRating(), u.getRegisteredAt()));
        }

        List<String> orderLines = new ArrayList<>();
        orderLines.add(line("ID", "Название", "Описание", "Категория", "Бюджет", "Дедлайн",
                "Статус", "ID заказчика", "ID исполнителя", "Создан"));
        for (Order o : orders) {
            orderLines.add(line(o.getId(), o.getTitle(), o.getDescription(), o.getCategory(), o.getBudget(),
                    o.getDeadline(), o.getStatus(), o.getCustomerId(), o.getFreelancerId(), o.getCreatedAt()));
        }

        return List.of(
                write(directory, "users.csv", userLines),
                write(directory, "orders.csv", orderLines));
    }

    /** Записывает строки в файл и возвращает его путь. */
    private Path write(Path directory, String fileName, List<String> lines) {
        Path path = directory.resolve(fileName);
        try {
            // В свежем клоне папки export/ может не быть
            Files.createDirectories(directory);
            try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                // BOM: по нему Excel понимает, что файл в UTF-8, иначе кириллица станет кракозябрами
                writer.write('\uFEFF');
                for (String line : lines) {
                    writer.write(line);
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Не удалось сохранить файл " + path + ": " + e.getMessage(), e);
        }
        return path;
    }

    /** Собирает одну строку CSV из значений. */
    private String line(Object... values) {
        return Arrays.stream(values)
                .map(this::escape)
                .collect(Collectors.joining(SEPARATOR));
    }

    /**
     * null — пустое поле. Значение с «;», кавычкой или переводом строки берём в кавычки
     * и удваиваем внутренние кавычки, иначе оно разобьётся на несколько колонок или строк.
     * Даты через toString() — это ISO-формат 2026-09-22.
     */
    private String escape(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        // Русский Excel ждёт дробную часть через запятую: «4.8» он откроет как текст или даже как дату 4 августа
        if (value instanceof Number) {
            text = text.replace('.', ',');
        }
        if (text.contains(SEPARATOR) || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
