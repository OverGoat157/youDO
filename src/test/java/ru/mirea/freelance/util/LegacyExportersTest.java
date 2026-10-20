package ru.mirea.freelance.util;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.mirea.freelance.model.*;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LegacyExportersTest {
    @TempDir Path directory;
    private final User user = new User(1L, "Анна", "anna@example.org", UserRole.CUSTOMER, 4.5, LocalDate.now());
    private final Order order = new Order(2L, "Название; с кавычкой \"", "Строка\nещё строка", OrderCategory.DESIGN,
            new BigDecimal("1234.56"), LocalDate.now().plusDays(1), OrderStatus.OPEN, 1L, null, LocalDateTime.now());

    @Test
    void excelCanBeReadBackWithNumericMoneyDatesAndAnEmptyFreelancer() throws Exception {
        Path file = new ExcelExporter().export(List.of(user), List.of(order), directory).getFirst();
        try (var input = Files.newInputStream(file); var workbook = new XSSFWorkbook(input)) {
            assertEquals(2, workbook.getNumberOfSheets());
            assertEquals("Анна", workbook.getSheet("Пользователи").getRow(1).getCell(1).getStringCellValue());
            var row = workbook.getSheet("Заказы").getRow(1);
            assertEquals(order.getTitle(), row.getCell(1).getStringCellValue());
            assertEquals(CellType.NUMERIC, row.getCell(4).getCellType());
            assertEquals(1234.56, row.getCell(4).getNumericCellValue(), 0.000001);
            assertTrue(DateUtil.isCellDateFormatted(row.getCell(5)));
            assertEquals(order.getDeadline(), row.getCell(5).getLocalDateTimeCellValue().toLocalDate());
            assertNull(row.getCell(8));
        }
    }

    @Test
    void oldCsvContractStillProducesTwoUtf8FilesWithEscapedText() throws Exception {
        List<Path> files = new CsvExporter().export(List.of(user), List.of(order), directory);
        assertEquals(2, files.size());
        for (Path file : files) assertEquals('\uFEFF', Files.readString(file).charAt(0));
        String text = Files.readString(directory.resolve("orders.csv"));
        assertTrue(text.contains("1234,56"));
        assertTrue(text.contains("\"Название; с кавычкой \"\"\""));
        assertTrue(text.contains("\"Строка\nещё строка\""));
    }
}
