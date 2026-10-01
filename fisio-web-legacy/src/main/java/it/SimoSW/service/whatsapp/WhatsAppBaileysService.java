package it.SimoSW.service.whatsapp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.SimoSW.util.AppProperties;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class WhatsAppBaileysService {

    private static final String DEFAULT_GATEWAY_BASE_URL = "http://127.0.0.1:3001";
    private static final int DEFAULT_TIMEOUT_MS = 4000;

    private final ObjectMapper objectMapper;
    private final String gatewayBaseUrl;
    private final int timeoutMs;
    private final String serviceDirectory;

    public WhatsAppBaileysService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.gatewayBaseUrl = trimTrailingSlash(AppProperties.get("whatsapp.baileys.gatewayBaseUrl", DEFAULT_GATEWAY_BASE_URL));
        this.timeoutMs = AppProperties.getInt("whatsapp.baileys.gatewayTimeoutMs", DEFAULT_TIMEOUT_MS);
        this.serviceDirectory = AppProperties.get("whatsapp.baileys.serviceDirectory", "/opt/baileys-service");
    }

    public BaileysStatus getStatus() {
        HttpURLConnection connection = null;
        try {
            URL endpoint = new URL(gatewayBaseUrl + "/api/status");
            connection = (HttpURLConnection) endpoint.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                return BaileysStatus.unreachable("Servizio WhatsApp locale non disponibile.");
            }

            Map<String, Object> response = readJsonBody(connection);
            return new BaileysStatus(
                    true,
                    Boolean.TRUE.equals(response.get("ready")),
                    Boolean.TRUE.equals(response.get("qrRequired")),
                    stringValue(response.get("state"), "UNKNOWN"),
                    stringValue(response.get("lastError"), null),
                    stringValue(response.get("qrUpdatedAt"), null),
                    intValue(response.get("qrCounter"))
            );
        } catch (IOException ex) {
            return BaileysStatus.unreachable("Servizio WhatsApp locale non raggiungibile.");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public String readQrHtml() {
        HttpURLConnection connection = null;
        try {
            URL endpoint = new URL(gatewayBaseUrl + "/api/qr");
            connection = (HttpURLConnection) endpoint.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            try (InputStream input = connection.getInputStream()) {
                return new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException ex) {
            return "<!doctype html><html><head><meta charset=\"utf-8\"><meta http-equiv=\"refresh\" content=\"2\"></head>"
                    + "<body><p>QR non disponibile. Avvia il servizio WhatsApp dalle impostazioni.</p></body></html>";
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public void startService() {
        BaileysStatus status = getStatus();
        if (status.isReachable()) {
            return;
        }

        File directory = resolveServiceDirectory();
        File startScript = new File(directory, "start-baileys.sh");
        if (!directory.canWrite()) {
            throw new IllegalStateException("Cartella Baileys non scrivibile dall'utente Tomcat: " + directory.getAbsolutePath());
        }

        File logFile = new File(directory, "baileys-service.log");
        ProcessBuilder processBuilder = new ProcessBuilder(resolveBashCommand(), startScript.getAbsolutePath());
        processBuilder.directory(directory);
        processBuilder.environment().put("PATH", "/usr/local/bin:/usr/bin:/bin");
        processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));
        processBuilder.redirectError(ProcessBuilder.Redirect.appendTo(logFile));
        try {
            processBuilder.start();
        } catch (IOException ex) {
            throw new RuntimeException("Impossibile avviare il servizio WhatsApp locale: " + ex.getMessage(), ex);
        }
    }

    public void stopService() {
        BaileysStatus status = getStatus();
        if (status.isReachable()) {
            if (requestShutdownViaGateway()) {
                return;
            }
        }

        File directory = resolveServiceDirectory();
        File stopScript = new File(directory, "stop-baileys.sh");
        if (!stopScript.isFile()) {
            throw new IllegalStateException("Script stop Baileys non trovato: " + stopScript.getAbsolutePath());
        }
        File logFile = new File(directory, "baileys-service.log");
        ProcessBuilder processBuilder = new ProcessBuilder(resolveBashCommand(), stopScript.getAbsolutePath());
        processBuilder.directory(directory);
        processBuilder.environment().put("PATH", "/usr/local/bin:/usr/bin:/bin");
        processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));
        processBuilder.redirectError(ProcessBuilder.Redirect.appendTo(logFile));
        try {
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("Arresto del servizio WhatsApp non riuscito.");
            }
        } catch (IOException ex) {
            throw new RuntimeException("Impossibile arrestare il servizio WhatsApp locale: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Arresto del servizio WhatsApp interrotto.", ex);
        }
    }

    public void sendTextMessage(String destinationPhone, String message) {
        String recipient = sanitizePhone(destinationPhone);
        String normalizedMessage = requireMessage(message);
        HttpURLConnection connection = null;
        try {
            URL endpoint = new URL(gatewayBaseUrl + "/api/send");
            connection = (HttpURLConnection) endpoint.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            connection.setDoOutput(true);

            String json = objectMapper.writeValueAsString(Map.of(
                    "recipient", recipient,
                    "message", normalizedMessage
            ));
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(json.getBytes(StandardCharsets.UTF_8));
            }

            int status = connection.getResponseCode();
            Map<String, Object> response = readJsonBody(connection);
            if (status < 200 || status >= 300) {
                Object gatewayError = response.get("error");
                String messageFromGateway = gatewayError == null ? "Il Servizio WhatsApp non ha accettato il messaggio." : String.valueOf(gatewayError);
                throw new RuntimeException(messageFromGateway);
            }
            if (!Boolean.TRUE.equals(response.get("success")) || isBlank(response.get("messageId"))) {
                throw new RuntimeException("Il Servizio WhatsApp non ha confermato l'invio del messaggio.");
            }
        } catch (IOException ex) {
            throw new RuntimeException("Servizio WhatsApp locale non raggiungibile. Controllare il servizio di messaggistica.", ex);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String sanitizePhone(String rawPhone) {
        String digitsOnly = rawPhone == null ? "" : rawPhone.replaceAll("[^0-9]", "");
        if (digitsOnly.startsWith("00")) {
            digitsOnly = digitsOnly.substring(2);
        }
        if (digitsOnly.length() < 8 || digitsOnly.length() > 15) {
            throw new IllegalArgumentException("Numero WhatsApp non valido. Usa il formato internazionale, ad esempio 393331234567.");
        }
        return digitsOnly;
    }

    private String requireMessage(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            throw new IllegalArgumentException("Messaggio WhatsApp mancante.");
        }
        return rawMessage.trim();
    }

    private boolean isBlank(Object value) {
        return value == null || String.valueOf(value).isBlank();
    }

    private Map<String, Object> readJsonBody(HttpURLConnection connection) {
        try (InputStream input = connection.getErrorStream() != null
                ? connection.getErrorStream()
                : connection.getInputStream()) {
            if (input == null) {
                return Map.of();
            }
            return objectMapper.readValue(input, new TypeReference<>() {
            });
        } catch (IOException ex) {
            return Map.of();
        }
    }

    private boolean requestShutdownViaGateway() {
        HttpURLConnection connection = null;
        try {
            URL endpoint = new URL(gatewayBaseUrl + "/api/shutdown");
            connection = (HttpURLConnection) endpoint.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            connection.setDoOutput(true);
            connection.getOutputStream().write(new byte[0]);
            int status = connection.getResponseCode();
            return status >= 200 && status < 300;
        } catch (IOException ex) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_GATEWAY_BASE_URL;
        }
        String normalized = value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String stringValue(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? fallback : normalized;
    }

    private int intValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private File resolveServiceDirectory() {
        List<File> candidates = new ArrayList<>();
        candidates.add(Path.of(serviceDirectory).toFile());
        candidates.add(Path.of("/opt/baileys-service").toFile());
        candidates.add(Path.of(System.getProperty("user.dir"), "baileys-service").toFile());

        for (File candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            File startScript = new File(candidate, "start-baileys.sh");
            if (candidate.isDirectory() && startScript.isFile()) {
                return candidate;
            }
        }

        throw new IllegalStateException("Cartella Baileys non trovata: " + candidates.get(0).getAbsolutePath());
    }

    private String resolveBashCommand() {
        File bash = Path.of("/bin/bash").toFile();
        return bash.isFile() ? bash.getAbsolutePath() : "bash";
    }

    public static class BaileysStatus {
        private final boolean reachable;
        private final boolean ready;
        private final boolean qrRequired;
        private final String state;
        private final String lastError;
        private final String qrUpdatedAt;
        private final int qrCounter;

        public BaileysStatus(boolean reachable,
                             boolean ready,
                             boolean qrRequired,
                             String state,
                             String lastError,
                             String qrUpdatedAt,
                             int qrCounter) {
            this.reachable = reachable;
            this.ready = ready;
            this.qrRequired = qrRequired;
            this.state = state;
            this.lastError = lastError;
            this.qrUpdatedAt = qrUpdatedAt;
            this.qrCounter = qrCounter;
        }

        public static BaileysStatus unreachable(String message) {
            return new BaileysStatus(false, false, false, "OFFLINE", message, null, 0);
        }

        public boolean isReachable() {
            return reachable;
        }

        public boolean isReady() {
            return ready;
        }

        public boolean isQrRequired() {
            return qrRequired;
        }

        public String getState() {
            return state;
        }

        public String getLastError() {
            return lastError;
        }

        public String getQrUpdatedAt() {
            return qrUpdatedAt;
        }

        public int getQrCounter() {
            return qrCounter;
        }
    }
}
