package ru.mirea.freelance.util;

import ru.mirea.freelance.exception.DatabaseException;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Служебный вывод структуры и содержимого всех таблиц базы (пункт 8 меню).
 * Список таблиц и колонок программа берёт из метаданных JDBC, а не из кода.
 */
public class DbInspector {

    // Даёт подключение к базе (класс Дениса)
    private final DatabaseManager db;

    public DbInspector(DatabaseManager db) {
        this.db = db;
    }

    public void printAllTables() {
        // Одно подключение на весь вывод; try (...) закроет его в конце
        try (Connection connection = db.getConnection()) {
            // Метаданные — «справочник» базы: какие в ней таблицы и колонки
            DatabaseMetaData meta = connection.getMetaData();

            // Имена всех обычных таблиц схемы public; «%» значит «любое имя»
            List<String> tables = new ArrayList<>();
            try (ResultSet rs = meta.getTables(null, "public", "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    tables.add(rs.getString("TABLE_NAME"));
                }
            }

            for (String table : tables) {
                printStructure(meta, table);
                printRows(connection, table);
                System.out.println();
            }
        } catch (SQLException e) {
            // Ошибку JDBC превращаем в свою DatabaseException — меню ловит её и печатает текст
            throw new DatabaseException("Не удалось прочитать структуру базы: " + e.getMessage(), e);
        }
    }

    /** Печатает «Таблица users: id bigserial NOT NULL, name varchar(100) NOT NULL, …». */
    private void printStructure(DatabaseMetaData meta, String table) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (ResultSet rs = meta.getColumns(null, "public", table, "%")) {
            while (rs.next()) {
                String type = rs.getString("TYPE_NAME");
                // Размер показываем только у varchar: varchar(100)
                if (type.equals("varchar")) {
                    type += "(" + rs.getInt("COLUMN_SIZE") + ")";
                }
                String column = rs.getString("COLUMN_NAME") + " " + type;
                // NO — пустое значение запрещено, колонка обязательная
                if (rs.getString("IS_NULLABLE").equals("NO")) {
                    column += " NOT NULL";
                }
                columns.add(column);
            }
        }
        System.out.println("Таблица " + table + ": " + String.join(", ", columns));
    }

    /** Печатает все строки таблицы в виде выровненной текстовой таблицы. */
    private void printRows(Connection connection, String table) throws SQLException {
        // Имя таблицы пришло из метаданных базы, а не от пользователя, поэтому SQL-инъекции тут быть не может
        // и можно обычный Statement. PreparedStatement всё равно не умеет подставлять имя таблицы через «?».
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT * FROM " + table + " ORDER BY 1")) {
            // Сколько колонок и как они называются — из метаданных результата запроса
            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();

            // Сначала читаем всё в память: ширину колонки узнаём только по самому длинному значению
            List<String[]> rows = new ArrayList<>();
            // В JDBC колонки нумеруются с 1, а массив — с 0, отсюда i - 1
            String[] header = new String[columnCount];
            for (int i = 1; i <= columnCount; i++) {
                header[i - 1] = meta.getColumnLabel(i);
            }
            rows.add(header);
            while (rs.next()) {
                String[] row = new String[columnCount];
                for (int i = 1; i <= columnCount; i++) {
                    String value = rs.getString(i);
                    row[i - 1] = value == null ? "NULL" : value;
                }
                rows.add(row);
            }

            // Ширина колонки = длина самого длинного значения в ней (включая заголовок)
            int[] widths = new int[columnCount];
            for (String[] row : rows) {
                for (int i = 0; i < columnCount; i++) {
                    widths[i] = Math.max(widths[i], row[i].length());
                }
            }

            for (int r = 0; r < rows.size(); r++) {
                List<String> cells = new ArrayList<>();
                for (int i = 0; i < columnCount; i++) {
                    // %-10s — дополнить значение пробелами справа до 10 символов
                    cells.add(String.format("%-" + widths[i] + "s", rows.get(r)[i]));
                }
                String line = String.join(" | ", cells);
                System.out.println(line);
                // После заголовка — линия из дефисов
                if (r == 0) {
                    System.out.println("-".repeat(line.length()));
                }
            }
            System.out.println("Строк: " + (rows.size() - 1));
        }
    }
}
