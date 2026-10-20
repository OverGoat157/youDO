package ru.mirea.freelance.fx.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import ru.mirea.freelance.fx.AppContext;

public class MainController {

    @FXML private TabPane tabs;
    @FXML private Tab ordersTab;
    @FXML private Tab usersTab;
    @FXML private Tab statisticsTab;
    @FXML private OrdersController ordersViewController;
    @FXML private UsersController usersViewController;
    @FXML private StatisticsController statisticsViewController;

    public MainController(AppContext context) {
    }

    @FXML
    private void initialize() {
        tabs.getSelectionModel().selectedItemProperty()
                .addListener((observable, previous, selected) -> refresh(selected));
        refresh(tabs.getSelectionModel().getSelectedItem());
    }

    private void refresh(Tab tab) {
        if (tab == ordersTab) {
            ordersViewController.refresh();
        } else if (tab == usersTab) {
            usersViewController.refresh();
        } else if (tab == statisticsTab) {
            statisticsViewController.refresh();
        }
    }
}
