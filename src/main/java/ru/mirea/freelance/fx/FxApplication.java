package ru.mirea.freelance.fx;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import ru.mirea.freelance.fx.controller.StatisticsController;

import java.net.URL;

public class FxApplication extends Application {
    private static final String RESOURCES = "/ru/mirea/freelance/fx/";
    private AppContext context;

    @Override
    public void start(Stage stage) {
        try {
            context = new AppContext();
            boolean mainReady = getClass().getResource(RESOURCES + "main.fxml") != null;
            FXMLLoader loader = context.load(RESOURCES + (mainReady ? "main.fxml" : "startup.fxml"));
            Scene scene = new Scene(loader.<Parent>getRoot(), 1100, 720);
            URL css = getClass().getResource(RESOURCES + "app.css");
            if (css != null) {
                scene.getStylesheets().add(css.toExternalForm());
            }
            stage.setTitle("youDO — Биржа фриланса");
            stage.setMinWidth(800);
            stage.setMinHeight(560);
            stage.setScene(scene);
            stage.show();
            if (!mainReady) {
                StatisticsController statistics = (StatisticsController)
                        loader.getNamespace().get("statisticsViewController");
                statistics.refresh();
            }
        } catch (RuntimeException error) {
            if (context != null) {
                context.close();
            }
            Dialogs.error(stage.isShowing() ? stage : null, error.getMessage());
            Platform.exit();
        }
    }

    @Override
    public void stop() {
        if (context != null) {
            context.close();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
