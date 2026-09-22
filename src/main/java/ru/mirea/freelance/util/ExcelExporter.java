package ru.mirea.freelance.util;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import ru.mirea.freelance.model.Order;
import ru.mirea.freelance.model.User;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

/**
 * Экспорт в Excel через Apache POI: один файл youdo_export_yyyy-MM-dd.xlsx,
 * листы «Пользователи» и «Заказы».
 */
public class ExcelExporter implements Exporter {

    private static final String[] USER_HEADERS = {
            "ID", "Имя", "Email", "Роль", "Рейтинг", "Дата регистрации"
    };
    private static final String[] ORDER_HEADERS = {
            "ID", "Название", "Описание", "Категория", "Бюджет", "Дедлайн",
            "Статус", "ID заказчика", "ID исполнителя", "Создан"
    };

    @Override
    public List<Path> export(List<User> users, List<Order> orders, Path directory) {
        Path path = directory.resolve("youdo_export_" + LocalDate.now() + ".xlsx");
        try {
            // В свежем клоне папки export/ может не быть
            Files.createDirectories(directory);
            try (Workbook workbook = new XSSFWorkbook();
                 OutputStream out = Files.newOutputStream(path)) {
                writeUsers(workbook, users);
                writeOrders(workbook, orders);
                workbook.write(out);
            }
        } catch (IOException e) {
            throw new RuntimeException("Не удалось сохранить файл " + path + ": " + e.getMessage(), e);
        }
        return List.of(path);
    }

    private void writeUsers(Workbook workbook, List<User> users) {
        Sheet sheet = createSheet(workbook, "Пользователи", USER_HEADERS);
        CellStyle dateStyle = dateStyle(workbook, "yyyy-mm-dd");

        int rowNumber = 1;
        for (User user : users) {
            Row row = sheet.createRow(rowNumber++);
            row.createCell(0).setCellValue(user.getId());
            row.createCell(1).setCellValue(user.getName());
            row.createCell(2).setCellValue(user.getEmail());
            row.createCell(3).setCellValue(user.getRole().toString());
            row.createCell(4).setCellValue(user.getRating());

            Cell registeredAt = row.createCell(5);
            registeredAt.setCellValue(user.getRegisteredAt());
            registeredAt.setCellStyle(dateStyle);
        }
        autoSize(sheet, USER_HEADERS.length);
    }

    private void writeOrders(Workbook workbook, List<Order> orders) {
        Sheet sheet = createSheet(workbook, "Заказы", ORDER_HEADERS);
        CellStyle dateStyle = dateStyle(workbook, "yyyy-mm-dd");
        CellStyle dateTimeStyle = dateStyle(workbook, "yyyy-mm-dd hh:mm");

        int rowNumber = 1;
        for (Order order : orders) {
            Row row = sheet.createRow(rowNumber++);
            row.createCell(0).setCellValue(order.getId());
            row.createCell(1).setCellValue(order.getTitle());
            row.createCell(2).setCellValue(order.getDescription());
            row.createCell(3).setCellValue(order.getCategory().toString());
            // Бюджет пишем числом, а не строкой, чтобы в Excel по нему можно было считать
            row.createCell(4).setCellValue(order.getBudget().doubleValue());

            Cell deadline = row.createCell(5);
            deadline.setCellValue(order.getDeadline());
            deadline.setCellStyle(dateStyle);

            row.createCell(6).setCellValue(order.getStatus().toString());
            row.createCell(7).setCellValue(order.getCustomerId());
            // Исполнителя может не быть — тогда ячейка остаётся пустой
            if (order.getFreelancerId() != null) {
                row.createCell(8).setCellValue(order.getFreelancerId());
            }

            Cell createdAt = row.createCell(9);
            createdAt.setCellValue(order.getCreatedAt());
            createdAt.setCellStyle(dateTimeStyle);
        }
        autoSize(sheet, ORDER_HEADERS.length);
    }

    /** Создаёт лист и первую строку с жирными заголовками. */
    private Sheet createSheet(Workbook workbook, String name, String[] headers) {
        Font bold = workbook.createFont();
        bold.setBold(true);
        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFont(bold);

        Sheet sheet = workbook.createSheet(name);
        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        return sheet;
    }

    /** Excel хранит дату как число дней; без формата ячейка покажет, например, 46037 вместо даты. */
    private CellStyle dateStyle(Workbook workbook, String format) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat(format));
        return style;
    }

    /** Ширина считается по содержимому, поэтому вызывать только после заполнения листа. */
    private void autoSize(Sheet sheet, int columns) {
        for (int i = 0; i < columns; i++) {
            sheet.autoSizeColumn(i);
        }
    }
}
