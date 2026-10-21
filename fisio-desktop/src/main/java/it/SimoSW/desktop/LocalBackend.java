package it.SimoSW.desktop;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Starts the backend shipped with the Windows desktop package. */
final class LocalBackend {
    private static final String BASE_URL = BackendEndpoint.LOCAL_URL;
    private Process process;

    String start(String backendUrl) {
        if (!BASE_URL.equals(backendUrl)
                || !Boolean.getBoolean("fisio.desktop.bundledBackend")) return null;
        if (responds("/ready")) return null;
        if (responds("/health")) return "Il backend locale è attivo, ma MySQL non è disponibile.";

        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData == null || localAppData.isBlank()) return "LOCALAPPDATA non è disponibile.";
        Path dataDir = Path.of(localAppData, "Fisio-e-Sports", "backend");
        Path config = dataDir.resolve("config.properties");
        if (!Files.isRegularFile(config)) {
            try {
                Files.createDirectories(dataDir);
                Path appDir = Path.of(LocalBackend.class.getProtectionDomain()
                        .getCodeSource().getLocation().toURI()).getParent();
                Path example = appDir.resolve("config.properties.example");
                if (Files.isRegularFile(example)) {
                    Files.copy(example, dataDir.resolve("config.properties.example"),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception ignored) {
                // Il messaggio indica comunque dove preparare la configurazione privata.
            }
            return "Configurazione MySQL mancante: " + config;
        }
        try {
            Path appDir = Path.of(LocalBackend.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).getParent();
            Path backendJar = appDir.resolve("fisio-backend-0.1.0.jar");
            Path javaExe = Path.of(System.getProperty("java.home"), "bin", "java.exe");
            if (!Files.isRegularFile(backendJar) || !Files.isRegularFile(javaExe)) {
                return "Backend o runtime Java non trovato nel pacchetto Windows.";
            }
            Files.createDirectories(dataDir);
            ProcessBuilder builder = new ProcessBuilder(javaExe.toString(), "-jar", backendJar.toString());
            Map<String, String> env = builder.environment();
            env.keySet().removeIf(key -> key.toUpperCase(java.util.Locale.ROOT).startsWith("FISIO_DB_"));
            env.put("FISIO_DB_CONFIG_FILE", config.toString());
            env.put("FISIO_BACKEND_BIND_ADDRESS", "127.0.0.1");
            env.put("FISIO_BACKEND_PORT", "8081");
            builder.redirectErrorStream(true);
            builder.redirectOutput(ProcessBuilder.Redirect.appendTo(dataDir.resolve("backend.log").toFile()));
            process = builder.start();
            for (int attempt = 0; attempt < 30; attempt++) {
                if (responds("/ready")) return null;
                if (!process.isAlive()) return "Il backend locale si è arrestato. Controlla " + dataDir.resolve("backend.log");
                Thread.sleep(200);
            }
            stop();
            return "MySQL locale non è pronto. Controlla " + dataDir.resolve("backend.log");
        } catch (Exception exception) {
            stop();
            return "Impossibile avviare il backend locale: " + exception.getMessage();
        }
    }

    void stop() {
        if (process != null && process.isAlive()) process.destroy();
    }

    private static boolean responds(String path) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(BASE_URL + path).openConnection();
            connection.setConnectTimeout(250);
            connection.setReadTimeout(250);
            return connection.getResponseCode() == 200;
        } catch (IOException exception) {
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
