package ru.mirea.freelance.fx;

import javafx.fxml.FXML;
import javafx.scene.control.TabPane;
import ru.mirea.freelance.fx.controller.StatisticsController;
import ru.mirea.freelance.fx.controller.UsersController;

/** Доступ к готовым экранам до подключения главного окна заказов. */
public class StartupController {
    @FXML private TabPane tabs;
    @FXML private UsersController usersViewController;
    @FXML private StatisticsController statisticsViewController;

    public StartupController(AppContext context) {
    }

    @FXML
    private void initialize() {
        tabs.getSelectionModel().selectedIndexProperty().addListener((observable, old, selected) -> refresh());
        refresh();
    }

    private void refresh() {
        if (tabs.getSelectionModel().getSelectedIndex() == 0) {
            usersViewController.refresh();
        } else {
            statisticsViewController.refresh();
        }
    }
}
