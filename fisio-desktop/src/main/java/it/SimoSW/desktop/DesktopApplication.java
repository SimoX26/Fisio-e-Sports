package it.SimoSW.desktop;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import netscape.javascript.JSObject;

import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class DesktopApplication extends Application {
    private final ExecutorService tokenExecutor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "fisio-secure-token-store");
        thread.setDaemon(true);
        return thread;
    });

    @Override
    public void start(Stage stage) {
        URL page = DesktopApplication.class.getResource("/desktop/index.html");
        if (page == null) {
            throw new IllegalStateException("Interfaccia desktop non trovata");
        }

        WebView view = new WebView();
        SecureTokenStore tokenStore = new SecureTokenStore();
        DesktopBridge bridge = new DesktopBridge(view, page.toExternalForm(), tokenStore, tokenExecutor);
        view.getEngine().getLoadWorker().stateProperty().addListener((observable, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED && page.toExternalForm().equals(view.getEngine().getLocation())) {
                JSObject window = (JSObject) view.getEngine().executeScript("window");
                window.setMember("desktopBridge", bridge);
                view.getEngine().executeScript("window.desktopBridgeReady && window.desktopBridgeReady()");
            }
        });
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

    @Override
    public void stop() {
        tokenExecutor.shutdownNow();
    }

    public static final class DesktopBridge {
        private final WebView view;
        private final String trustedPage;
        private final SecureTokenStore store;
        private final ExecutorService executor;

        DesktopBridge(WebView view, String trustedPage, SecureTokenStore store, ExecutorService executor) {
            this.view = view;
            this.trustedPage = trustedPage;
            this.store = store;
            this.executor = executor;
        }

        public void loadToken() {
            executor.execute(() -> {
                String token = store.load();
                Platform.runLater(() -> {
                    if (trustedPage.equals(view.getEngine().getLocation())) {
                        String literal = token == null ? "null" : "\"" + token + "\"";
                        view.getEngine().executeScript("window.desktopTokenLoaded && window.desktopTokenLoaded(" + literal + ")");
                    }
                });
            });
        }

        public void saveToken(String token) {
            executor.execute(() -> {
                boolean saved = store.save(token);
                Platform.runLater(() -> {
                    if (trustedPage.equals(view.getEngine().getLocation())) {
                        view.getEngine().executeScript("window.desktopTokenStored && window.desktopTokenStored(" + saved + ")");
                    }
                });
            });
        }

        public boolean clearToken() {
            try {
                return executor.submit(store::clear).get(35, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return false;
            } catch (Exception exception) {
                return false;
            }
        }
    }
}
