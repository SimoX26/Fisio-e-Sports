package it.SimoSW.desktop;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import netscape.javascript.JSObject;

import java.net.URL;
import java.net.MalformedURLException;
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
        String backendUrl = BackendEndpoint.resolve(System.getenv("FISIO_BACKEND_URL"));
        SecureTokenStore tokenStore = new SecureTokenStore(backendUrl);
        EncryptedPatientCache patientCache = new EncryptedPatientCache(backendUrl);
        DesktopBridge bridge = new DesktopBridge(view, page, tokenStore, patientCache, tokenExecutor);
        view.getEngine().getLoadWorker().stateProperty().addListener((observable, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                if (isTrustedPage(page, view.getEngine().getLocation())) {
                    JSObject window = (JSObject) view.getEngine().executeScript("window");
                    window.setMember("fisioBackendUrl", backendUrl);
                    window.setMember("desktopBridge", bridge);
                    view.getEngine().executeScript("window.desktopBridgeReady && window.desktopBridgeReady()");
                } else {
                    System.err.println("Impossibile inizializzare il desktop: pagina locale non riconosciuta");
                }
            } else if (newState == Worker.State.FAILED) {
                Throwable failure = view.getEngine().getLoadWorker().getException();
                String detail = failure == null ? "Errore sconosciuto" : failure.toString();
                System.err.println("Impossibile caricare l'interfaccia desktop: " + detail);
                stage.setScene(new Scene(new Label("Impossibile caricare l'interfaccia desktop.\n" + detail), 840, 600));
            }
        });
        view.getEngine().load(page.toExternalForm());
        stage.setTitle("Fisio e Sports — prototipo desktop");
        stage.setMinWidth(840);
        stage.setMinHeight(600);
        stage.setScene(new Scene(view, 1120, 760));
        stage.show();
        DesktopUpdater.checkOnStartup(stage);
    }

    public static void main(String[] args) {
        launch(args);
    }

    private static boolean isTrustedPage(URL page, String location) {
        try {
            return location != null && page.sameFile(new URL(location));
        } catch (MalformedURLException exception) {
            return false;
        }
    }

    @Override
    public void stop() {
        tokenExecutor.shutdown();
        try {
            if (!tokenExecutor.awaitTermination(35, TimeUnit.SECONDS)) tokenExecutor.shutdownNow();
        } catch (InterruptedException exception) {
            tokenExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public static final class DesktopBridge {
        private final WebView view;
        private final URL trustedPage;
        private final SecureTokenStore store;
        private final EncryptedPatientCache patientCache;
        private final ExecutorService executor;

        DesktopBridge(WebView view, URL trustedPage, SecureTokenStore store,
                      EncryptedPatientCache patientCache, ExecutorService executor) {
            this.view = view;
            this.trustedPage = trustedPage;
            this.store = store;
            this.patientCache = patientCache;
            this.executor = executor;
        }

        public String loadPatientCache(String token) {
            return isTrustedPage(trustedPage, view.getEngine().getLocation()) ? patientCache.load(token) : null;
        }

        public void savePatientCache(String token, String json) {
            if (!isTrustedPage(trustedPage, view.getEngine().getLocation())) return;
            executor.execute(() -> {
                boolean saved = patientCache.save(token, json);
                Platform.runLater(() -> {
                    if (isTrustedPage(trustedPage, view.getEngine().getLocation())) {
                        ((JSObject) view.getEngine().executeScript("window"))
                                .call("desktopPatientCacheSaved", saved);
                    }
                });
            });
        }

        public void loadToken() {
            executor.execute(() -> {
                String token = store.load();
                Platform.runLater(() -> {
                    if (isTrustedPage(trustedPage, view.getEngine().getLocation())) {
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
                    if (isTrustedPage(trustedPage, view.getEngine().getLocation())) {
                        view.getEngine().executeScript("window.desktopTokenStored && window.desktopTokenStored(" + saved + ")");
                    }
                });
            });
        }

        public boolean clearToken() {
            try {
                return executor.submit(() -> {
                    boolean cacheCleared = patientCache.clear();
                    boolean tokenCleared = store.clear();
                    return cacheCleared && tokenCleared;
                }).get(35, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return false;
            } catch (Exception exception) {
                return false;
            }
        }
    }
}
