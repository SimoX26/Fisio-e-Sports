package it.SimoSW.desktop;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import netscape.javascript.JSObject;

import java.net.URL;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.io.IOException;
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
        String version = System.getProperty("fisio.desktop.version", "sviluppo");
        recordStartup("Avvio " + version);
        URL page = DesktopApplication.class.getResource("/desktop/index.html");
        if (page == null) {
            throw new IllegalStateException("Interfaccia desktop non trovata");
        }

        WebView view = new WebView();
        view.getEngine().setOnError(event -> recordStartup("WebEngine: " + event.getMessage()));
        String backendUrl = BackendEndpoint.resolve(System.getenv("FISIO_BACKEND_URL"));
        SecureTokenStore tokenStore = new SecureTokenStore(backendUrl);
        EncryptedPatientCache patientCache = new EncryptedPatientCache(backendUrl);
        DesktopBridge bridge = new DesktopBridge(view, page, tokenStore, patientCache, tokenExecutor);
        view.getEngine().getLoadWorker().stateProperty().addListener((observable, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                if (isTrustedPage(page, view.getEngine().getLocation())) {
                    try {
                        recordStartup("Pagina caricata: " + view.getEngine().executeScript("document.readyState"));
                        if (!Boolean.TRUE.equals(view.getEngine().executeScript(
                                "Boolean(document.getElementById('loginScreen'))"))) {
                            throw new IllegalStateException("Schermata di login assente dalla pagina locale");
                        }
                        JSObject window = (JSObject) view.getEngine().executeScript("window");
                        window.setMember("fisioBackendUrl", backendUrl);
                        window.setMember("desktopBridge", bridge);
                        view.getEngine().executeScript("window.desktopBridgeReady && window.desktopBridgeReady()");
                        recordStartup("Collegamento JavaScript pronto");
                    } catch (RuntimeException | LinkageError failure) {
                        showStartupError(stage, "Inizializzazione della pagina fallita: " + failure);
                    }
                } else {
                    showStartupError(stage, "Pagina locale non riconosciuta");
                }
            } else if (newState == Worker.State.FAILED) {
                Throwable failure = view.getEngine().getLoadWorker().getException();
                String detail = failure == null ? "Errore sconosciuto" : failure.toString();
                showStartupError(stage, "Caricamento della pagina fallito: " + detail);
            }
        });
        stage.setTitle("Fisio e Sports — " + version);
        URL icon = DesktopApplication.class.getResource("/desktop/assets/img/logo.png");
        if (icon != null) stage.getIcons().add(new Image(icon.toExternalForm()));
        stage.setMinWidth(840);
        stage.setMinHeight(600);
        stage.setScene(new Scene(view, 1120, 760));
        stage.show();
        view.getEngine().load(page.toExternalForm());
        DesktopUpdater.checkOnStartup(stage);
    }

    private static void showStartupError(Stage stage, String detail) {
        recordStartup(detail);
        System.err.println("Impossibile avviare il desktop: " + detail);
        stage.setScene(new Scene(new Label("Impossibile avviare il desktop.\n" + detail), 840, 600));
    }

    private static void recordStartup(String message) {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData == null || localAppData.isBlank()) return;
        try {
            Path log = Path.of(localAppData, "Fisio-e-Sports", "desktop-startup.log");
            Files.createDirectories(log.getParent());
            Files.writeString(log, Instant.now() + " " + message + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException ignored) {
            // La diagnostica non deve impedire l'avvio dell'applicazione.
        }
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
