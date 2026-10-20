package ru.mirea.freelance.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.mirea.freelance.exception.BusinessException;
import ru.mirea.freelance.model.Order;
import ru.mirea.freelance.model.OrderCategory;
import ru.mirea.freelance.model.OrderStatus;
import ru.mirea.freelance.model.User;
import ru.mirea.freelance.model.UserRole;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrderCsvExporterTest {

    private static final List<String> HEADER = List.of(
            "Название", "Описание", "Категория", "Бюджет", "Дедлайн",
            "Статус", "Заказчик", "Исполнитель", "Создан");

    @TempDir
    Path dir;

    private final OrderCsvExporter exporter = new OrderCsvExporter();
    private final User customer = new User(1L, "Анна Смирнова", "anna@mail.ru", UserRole.CUSTOMER, 4.8, LocalDate.of(2026, 1, 15));
    private final User freelancer = new User(4L, "Дмитрий Волков", "dmitry@mail.ru", UserRole.FREELANCER, 4.9, LocalDate.of(2026, 1, 20));

    private Order order(long id, String title, String description, String budget, Long freelancerId) {
        return new Order(id, title, description, OrderCategory.DESIGN, new BigDecimal(budget),
                LocalDate.of(2026, 10, 5), OrderStatus.IN_PROGRESS, 1L, freelancerId,
                LocalDateTime.of(2026, 9, 18, 10, 5));
    }

    @Test
    void export_complexFields_readBackUnchanged() throws IOException {
        Order tricky = order(7, "Логотип \"Кофе\"", "Цвета; шрифты\nвторая строка", "15000", 4L);
        Path file = dir.resolve("orders.csv");

        exporter.export(List.of(tricky), List.of(customer, freelancer), file);

        List<List<String>> rows = read(file);
        assertEquals(2, rows.size());
        assertEquals(HEADER, rows.get(0));
        assertEquals(List.of("Логотип \"Кофе\"", "Цвета; шрифты\nвторая строка", "DESIGN", "15000,00",
                "05.10.2026", "IN_PROGRESS", "Анна Смирнова (anna@mail.ru)", "Дмитрий Волков (dmitry@mail.ru)",
                "18.09.2026 10:05"), rows.get(1));
    }

    @Test
    void export_keepsGivenOrderAndOnlyGivenRows() throws IOException {
        // Как после сортировки/фильтра в таблице: id идут не по возрастанию, часть заказов отсутствует
        List<Order> visible = List.of(order(9, "Третий", null, "300", null),
                order(2, "Первый", null, "100", null),
                order(5, "Второй", null, "200", null));
        Path file = dir.resolve("orders.csv");

        exporter.export(visible, List.of(customer), file);

        List<List<String>> rows = read(file);
        assertEquals(4, rows.size());
        assertEquals(List.of("Третий", "Первый", "Второй"),
                List.of(rows.get(1).get(0), rows.get(2).get(0), rows.get(3).get(0)));
    }

    @Test
    void export_noFreelancerAndNoDescription_emptyCells() throws IOException {
        Path file = dir.resolve("orders.csv");

        exporter.export(List.of(order(1, "Сайт", null, "45000.5", null)), List.of(customer), file);

        List<String> row = read(file).get(1);
        assertEquals("", row.get(1));
        assertEquals("45000,50", row.get(3));
        assertEquals("", row.get(7));
    }

    @Test
    void export_emptyList_writesHeaderOnly() throws IOException {
        Path file = dir.resolve("empty.csv");

        exporter.export(List.of(), List.of(), file);

        assertEquals(List.of(HEADER), read(file));
    }

    @Test
    void export_writesUtf8WithBom() throws IOException {
        Path file = dir.resolve("orders.csv");

        exporter.export(List.of(), List.of(), file);

        byte[] bytes = Files.readAllBytes(file);
        assertEquals((byte) 0xEF, bytes[0]);
        assertEquals((byte) 0xBB, bytes[1]);
        assertEquals((byte) 0xBF, bytes[2]);
    }

    @Test
    void export_pathIsDirectory_throwsBusinessExceptionWithPath() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> exporter.export(List.of(), List.of(), dir));
        assertTrue(e.getMessage().contains(dir.toString()));
    }

    /** Разбор CSV с учётом кавычек: «;» и перевод строки внутри кавычек — часть значения, "" — одна кавычка. */
    private List<List<String>> read(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        assertEquals('﻿', text.charAt(0));
        text = text.substring(1);

        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    cell.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ';') {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(cell.toString());
                cell.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else {
                cell.append(c);
            }
        }
        return rows;
    }
}
