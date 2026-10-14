package it.SimoSW.desktop;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Properties;

/** Downloads verified Windows installers without replacing files of a running application. */
final class DesktopUpdater {
    private static final String RELEASE = "https://github.com/SimoX26/Fisio-e-Sports/releases/latest/download/";
    private static final long MAX_INSTALLER_BYTES = 500_000_000L;
    private static final int MAX_MANIFEST_BYTES = 8192;

    private DesktopUpdater() { }

    static void checkOnStartup(Stage stage) {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                || !"exe".equals(System.getProperty("fisio.desktop.installType"))) return;
        String installed = System.getProperty("fisio.desktop.version", "");
        if (!validVersion(installed)) return;
        Thread worker = new Thread(() -> {
            try {
                HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                        .connectTimeout(Duration.ofSeconds(10)).build();
                HttpRequest manifestRequest = HttpRequest.newBuilder(URI.create(RELEASE + "desktop-update.properties"))
                        .timeout(Duration.ofSeconds(20)).GET().build();
                HttpResponse<InputStream> manifestResponse = client.send(manifestRequest, HttpResponse.BodyHandlers.ofInputStream());
                if (manifestResponse.statusCode() != 200) {
                    manifestResponse.body().close();
                    return;
                }
                Properties manifest = new Properties();
                try (InputStream input = manifestResponse.body()) {
                    byte[] bytes = input.readNBytes(MAX_MANIFEST_BYTES + 1);
                    if (bytes.length > MAX_MANIFEST_BYTES) return;
                    manifest.load(new java.io.ByteArrayInputStream(bytes));
                }
                String version = manifest.getProperty("version", "");
                String expectedHash = manifest.getProperty("sha256", "").toLowerCase(Locale.ROOT);
                if (!validVersion(version) || compareVersions(version, installed) <= 0
                        || !expectedHash.matches("[0-9a-f]{64}")) return;

                String localAppData = System.getenv("LOCALAPPDATA");
                if (localAppData == null || localAppData.isBlank()) return;
                Path directory = Path.of(localAppData, "Fisio-e-Sports", "updates");
                Path installer = directory.resolve("Fisio-e-Sports-" + version + ".exe");
                if (!Files.isRegularFile(installer) || Files.size(installer) > MAX_INSTALLER_BYTES
                        || !expectedHash.equals(sha256(installer))) {
                    Files.createDirectories(directory);
                    Path temporary = Files.createTempFile(directory, "download-", ".tmp");
                    try {
                        HttpRequest downloadRequest = HttpRequest.newBuilder(URI.create(RELEASE + "Fisio-e-Sports.exe"))
                                .timeout(Duration.ofMinutes(5)).GET().build();
                        HttpResponse<InputStream> response = client.send(downloadRequest, HttpResponse.BodyHandlers.ofInputStream());
                        try (InputStream input = response.body()) {
                            if (response.statusCode() != 200) return;
                            copyBounded(input, temporary);
                        }
                        if (!expectedHash.equals(sha256(temporary))) return;
                        Files.move(temporary, installer, StandardCopyOption.REPLACE_EXISTING);
                    } finally {
                        Files.deleteIfExists(temporary);
                    }
                }
                Platform.runLater(() -> showReady(stage, installer, version));
            } catch (Exception exception) {
                // La disponibilità degli aggiornamenti non blocca l'app o l'accesso ai dati.
            }
        }, "fisio-desktop-update");
        worker.setDaemon(true);
        worker.start();
    }

    private static void showReady(Stage stage, Path installer, String version) {
        if (!stage.isShowing()) return;
        Alert alert = new Alert(Alert.AlertType.INFORMATION,
                "La versione " + version + " è pronta. Apri l'installer per aggiornare l'applicazione.",
                new ButtonType("Apri installer"), new ButtonType("Più tardi"));
        alert.initOwner(stage);
        alert.setTitle("Aggiornamento disponibile");
        alert.setHeaderText("Nuova versione scaricata");
        alert.showAndWait().ifPresent(choice -> {
            if (!"Apri installer".equals(choice.getText())) return;
            try {
                Desktop.getDesktop().open(installer.toFile());
                Platform.exit();
            } catch (IOException | UnsupportedOperationException exception) {
                new Alert(Alert.AlertType.ERROR, "Apri manualmente: " + installer).showAndWait();
            }
        });
    }

    private static void copyBounded(InputStream input, Path target) throws IOException {
        try (var output = Files.newOutputStream(target)) {
            byte[] buffer = new byte[64 * 1024];
            long total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_INSTALLER_BYTES) throw new IOException("Installer troppo grande");
                output.write(buffer, 0, count);
            }
        }
    }

    private static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new DigestInputStream(Files.newInputStream(file), digest)) {
            input.transferTo(java.io.OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static boolean validVersion(String value) {
        return value.matches("\\d+(\\.\\d+){0,3}");
    }

    private static int compareVersions(String left, String right) {
        String[] a = left.split("\\.");
        String[] b = right.split("\\.");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? Integer.parseInt(a[i]) : 0;
            int y = i < b.length ? Integer.parseInt(b[i]) : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }
}
