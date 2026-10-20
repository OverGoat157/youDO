package ru.mirea.freelance.fx;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import ru.mirea.freelance.model.Order;
import ru.mirea.freelance.model.User;
import ru.mirea.freelance.util.OrderCsvExporter;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Экспорт видимых заказов в CSV: выбор файла в диалоге, запись в фоне, сообщение о результате. */
public class ExportAction {

    private final AppContext context;
    private final OrderCsvExporter exporter = new OrderCsvExporter();
    private File lastDirectory;
    private boolean exporting;

    public ExportAction(AppContext context) {
        this.context = context;
    }

    /** Вызывается в UI-потоке. orders — снимок строк таблицы в видимом порядке, базу не перечитываем. */
    public void exportOrders(Window owner, List<Order> orders, List<User> users) {
        if (exporting) {
            showInfo(owner, "Экспорт уже выполняется. Дождитесь его завершения.");
            return;
        }
        // Копии: пока открыт диалог и идёт запись, таблица может измениться, а в файл должно попасть то, что видел пользователь
        List<Order> ordersCopy = new ArrayList<>(orders);
        List<User> usersCopy = new ArrayList<>(users);

        File chosen = chooseFile(owner);
        if (chosen == null) {
            return; // отмена — не ошибка и не сообщение
        }
        Path file = chosen.toPath();
        // Системный диалог сам спрашивает про перезапись выбранного файла; если мы дописали .csv,
        // он про такое имя не знает — спрашиваем сами
        if (!chosen.getName().contains(".")) {
            file = file.resolveSibling(chosen.getName() + ".csv");
            if (file.toFile().exists() && !confirmOverwrite(owner, file)) {
                return;
            }
        }
        lastDirectory = file.toAbsolutePath().getParent().toFile();

        Path target = file;
        exporting = true;
        context.tasks().run(
                () -> {
                    exporter.export(ordersCopy, usersCopy, target);
                    return target;
                },
                saved -> {
                    exporting = false;
                    showInfo(owner, "Заказов сохранено: " + ordersCopy.size() + "\nФайл: " + saved.toAbsolutePath());
                },
                error -> {
                    exporting = false;
                    String reason = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
                    Dialogs.error(owner, reason);
                });
    }

    /** null — пользователь нажал «Отмена». */
    File chooseFile(Window owner) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Экспорт заказов в CSV");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV (*.csv)", "*.csv"));
        chooser.setInitialFileName("youdo_orders_" + LocalDate.now() + ".csv");
        File directory = lastDirectory != null ? lastDirectory : new File("export");
        if (directory.isDirectory()) {
            chooser.setInitialDirectory(directory);
        }
        return chooser.showSaveDialog(owner);
    }

    boolean confirmOverwrite(Window owner, Path file) {
        ButtonType overwrite = new ButtonType("Перезаписать", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Файл уже существует:\n" + file + "\nПерезаписать его?",
                overwrite, cancel);
        alert.initOwner(owner);
        alert.setTitle("Экспорт заказов");
        alert.setHeaderText(null);
        return alert.showAndWait().filter(button -> button == overwrite).isPresent();
    }

    void showInfo(Window owner, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message);
        alert.initOwner(owner);
        alert.setTitle("Экспорт заказов");
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
