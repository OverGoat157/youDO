package ru.mirea.freelance.fx.controller;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import ru.mirea.freelance.fx.AppContext;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Вкладка «Статистика»: показатели по всей базе данных. Считает StatisticsService, экран только показывает. */
public class StatisticsController {

    // Показатели, которые выносим в крупные карточки; остальные видны в таблице
    private static final List<String> HEADLINE = List.of(
            "Всего пользователей", "Всего заказов", "Средний бюджет",
            "Сумма бюджетов активных заказов", "Просроченных заказов");

    private final AppContext context;

    @FXML private Button refreshButton;
    @FXML private ProgressIndicator progress;
    @FXML private Label errorLabel;
    @FXML private Label emptyLabel;
    @FXML private FlowPane cards;
    @FXML private TableView<Row> table;
    @FXML private TableColumn<Row, String> nameColumn;
    @FXML private TableColumn<Row, String> valueColumn;

    private final ObservableList<Row> rows = FXCollections.observableArrayList();
    private boolean loading;
    private boolean refreshAgain;

    /** Одна строка таблицы: название показателя и уже отформатированное значение. */
    private record Row(String name, String value) {
    }

    public StatisticsController(AppContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        nameColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().name()));
        valueColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().value()));
        table.setItems(rows);
        table.setPlaceholder(new Label("Нет данных"));
    }

    @FXML
    private void onRefresh() {
        refresh();
    }

    /** Пересчитывает статистику по всей текущей БД. Главное окно вызывает его при переходе на вкладку. */
    public void refresh() {
        // Пока идёт загрузка, второй запрос не запускаем, а выполним после неё:
        // так старый ответ не затрёт более новый
        if (loading) {
            refreshAgain = true;
            return;
        }
        loading = true;
        setBusy(true);
        // Чтение БД — в фоне, показ результата — в UI-потоке (оба callback вызывает FxTasks)
        context.tasks().run(() -> context.statistics().collect(), this::show, this::showError);
    }

    private void show(Map<String, Object> stats) {
        rows.clear();
        cards.getChildren().clear();
        for (Map.Entry<String, Object> entry : stats.entrySet()) {
            String value = format(entry.getValue());
            rows.add(new Row(entry.getKey(), value));
            if (HEADLINE.contains(entry.getKey())) {
                cards.getChildren().add(card(entry.getKey(), value));
            }
        }
        hideError();
        boolean noData = isZero(stats.get("Всего пользователей")) && isZero(stats.get("Всего заказов"));
        emptyLabel.setVisible(noData);
        emptyLabel.setManaged(noData);
        finish();
    }

    private void showError(Throwable error) {
        // Прежние цифры остаются на экране, сверху — причина без stack trace
        String reason = error.getMessage() == null || error.getMessage().isBlank()
                ? error.getClass().getSimpleName() : error.getMessage();
        errorLabel.setText("Не удалось загрузить статистику: " + reason);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
        finish();
    }

    /** Завершение загрузки: вернуть кнопку и, если во время загрузки просили обновить ещё раз, обновить. */
    private void finish() {
        loading = false;
        setBusy(false);
        if (refreshAgain) {
            refreshAgain = false;
            refresh();
        }
    }

    private void setBusy(boolean busy) {
        refreshButton.setDisable(busy);
        progress.setVisible(busy);
        progress.setManaged(busy);
    }

    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    private VBox card(String name, String value) {
        Label valueLabel = new Label(value);
        valueLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        Label nameLabel = new Label(name);
        nameLabel.setWrapText(true);
        VBox card = new VBox(4, valueLabel, nameLabel);
        card.getStyleClass().add("stat-card");
        card.setPrefWidth(190);
        return card;
    }

    private boolean isZero(Object value) {
        return value instanceof Number number && number.longValue() == 0;
    }

    /** Целые — с пробелами между тысячами, деньги — всегда с двумя знаками после запятой. */
    private String format(Object value) {
        Locale ru = Locale.forLanguageTag("ru-RU");
        if (value instanceof BigDecimal money) {
            NumberFormat fmt = NumberFormat.getNumberInstance(ru);
            fmt.setMinimumFractionDigits(2);
            fmt.setMaximumFractionDigits(2);
            return fmt.format(money);
        }
        if (value instanceof Number number) {
            return NumberFormat.getIntegerInstance(ru).format(number);
        }
        return String.valueOf(value);
    }
}
