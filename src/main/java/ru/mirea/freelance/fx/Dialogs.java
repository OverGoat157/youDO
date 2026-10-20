package ru.mirea.freelance.fx;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

/** Общие диалоги; вызываются из UI-потока. */
public final class Dialogs {
    private Dialogs() {
    }

    public static void error(Window owner, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(owner);
        alert.setTitle("Ошибка");
        alert.setHeaderText("Не удалось выполнить действие");
        alert.setContentText(message == null || message.isBlank() ? "Причина ошибки неизвестна." : message);
        alert.showAndWait();
    }

    public static boolean confirmDelete(Window owner, String entityName) {
        ButtonType delete = new ButtonType("Удалить", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Удалить «" + entityName + "»?", delete, cancel);
        alert.initOwner(owner);
        alert.setTitle("Подтверждение удаления");
        alert.setHeaderText(null);
        return alert.showAndWait().filter(delete::equals).isPresent();
    }
}
