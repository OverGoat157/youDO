package ru.mirea.freelance.util;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Scanner;

public class InputReader {

    private final Scanner scanner;

    public InputReader(Scanner scanner) {
        this.scanner = scanner;
    }

    public int readInt(String prompt) {
        while (true) {
            String line = readLine(prompt);
            try {
                return Integer.parseInt(line);
            } catch (NumberFormatException e) {
                System.out.println("Ошибка: нужно целое число.");
            }
        }
    }

    public long readLong(String prompt) {
        while (true) {
            String line = readLine(prompt);
            try {
                return Long.parseLong(line);
            } catch (NumberFormatException e) {
                System.out.println("Ошибка: нужно целое число.");
            }
        }
    }

    public BigDecimal readBigDecimal(String prompt) {
        while (true) {
            String line = readLine(prompt).replace(',', '.');
            try {
                return new BigDecimal(line);
            } catch (NumberFormatException e) {
                System.out.println("Ошибка: нужно число, например 1500 или 1500,50.");
            }
        }
    }

    public LocalDate readDate(String prompt) {
        while (true) {
            String line = readLine(prompt);
            try {
                return LocalDate.parse(line);
            } catch (DateTimeParseException e) {
                System.out.println("Ошибка: дата в формате гггг-мм-дд, например 2026-10-15.");
            }
        }
    }

    public String readNonEmpty(String prompt) {
        while (true) {
            String line = readLine(prompt);
            if (!line.isEmpty()) {
                return line;
            }
            System.out.println("Ошибка: значение не может быть пустым.");
        }
    }

    public String readOptional(String prompt) {
        String line = readLine(prompt);
        return line.isEmpty() ? null : line;
    }

    public <E extends Enum<E>> E readEnum(String prompt, Class<E> type) {
        System.out.println("Варианты: " + Arrays.toString(type.getEnumConstants()));
        while (true) {
            String line = readLine(prompt);
            try {
                return Enum.valueOf(type, line.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                System.out.println("Ошибка: выберите один из вариантов " + Arrays.toString(type.getEnumConstants()) + ".");
            }
        }
    }

    private String readLine(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine().trim();
    }
}
