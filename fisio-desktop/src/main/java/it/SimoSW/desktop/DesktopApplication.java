package it.SimoSW.desktop;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

import java.net.URL;

public final class DesktopApplication extends Application {

    @Override
    public void start(Stage stage) {
        URL page = DesktopApplication.class.getResource("/desktop/index.html");
        if (page == null) {
            throw new IllegalStateException("Interfaccia desktop non trovata");
        }

        WebView view = new WebView();
        view.getEngine().load(page.toExternalForm());
        stage.setTitle("Fisio e Sports — prototipo desktop");
        stage.setMinWidth(840);
        stage.setMinHeight(600);
        stage.setScene(new Scene(view, 1120, 760));
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
