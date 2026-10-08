package it.SimoSW.backend;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import it.SimoSW.model.User;
import it.SimoSW.util.AppProperties;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

final class WhatsAppControlApiHandler implements HttpHandler {
    private final TherapistAuthenticator authenticator;

    WhatsAppControlApiHandler(TherapistAuthenticator authenticator) {
        this.authenticator = authenticator;
    }

    static boolean isAvailable() {
        if (!"manual".equals(AppProperties.get("whatsapp.baileys.managementMode", "systemd"))) return false;
        String configured = AppProperties.get("whatsapp.baileys.serviceDirectory");
        if (configured == null || configured.isBlank()) return false;
        Path directory = Path.of(configured);
        Path session = directory.resolve("auth-session");
        Path log = directory.resolve("baileys-service.log");
        return directory.isAbsolute() && Files.isDirectory(directory) && Files.isWritable(directory)
                && Files.isRegularFile(directory.resolve("start-baileys.sh"))
                && (!Files.exists(session) || Files.isWritable(session))
                && (!Files.exists(log) || Files.isWritable(log));
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            ApiJson.allowLocalFileOrigin(exchange, "POST, OPTIONS", "Authorization, Content-Type");
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "POST");
                ApiJson.send(exchange, 405, "{\"error\":\"method_not_allowed\"}");
                return;
            }
            User user = authenticator.authenticate(exchange);
            Long therapistId = user == null ? null : authenticator.therapistId(user);
            if (therapistId == null) {
                ApiJson.send(exchange, 401, "{\"error\":\"unauthorized\"}");
                return;
            }
            if (!ReminderSendApiHandler.isConfigured(therapistId)) {
                ApiJson.send(exchange, 428, "{\"error\":\"whatsapp_not_configured\"}");
                return;
            }
            if (!isAvailable()) {
                ApiJson.send(exchange, 409, "{\"error\":\"control_unavailable\"}");
                return;
            }
            byte[] body = exchange.getRequestBody().readNBytes(1025);
            if (body.length > 1024) {
                ApiJson.send(exchange, 413, "{\"error\":\"request_too_large\"}");
                return;
            }
            Map<String, List<String>> form = ReminderSendApiHandler.parseForm(new String(body, StandardCharsets.UTF_8));
            List<String> actions = form.get("action");
            if (actions == null || actions.size() != 1 || !List.of("start", "stop").contains(actions.get(0))) {
                ApiJson.send(exchange, 400, "{\"error\":\"invalid_action\"}");
                return;
            }
            String action = actions.get(0);
            try {
                if ("start".equals(action)) start();
                else stop();
                ApiJson.send(exchange, 202, "{\"requested\":true,\"action\":" + ApiJson.quote(action) + "}");
            } catch (IOException exception) {
                System.err.println("Controllo WhatsApp non riuscito: " + exception.getClass().getSimpleName());
                ApiJson.send(exchange, 502, "{\"error\":\"control_failed\"}");
            }
        } catch (RuntimeException exception) {
            System.err.println("Errore API controllo WhatsApp: " + exception.getClass().getSimpleName());
            ApiJson.send(exchange, 503, "{\"error\":\"unavailable\"}");
        } finally {
            exchange.close();
        }
    }

    private static void start() throws IOException {
        if (gatewayReachable()) return;
        Path directory = Path.of(AppProperties.get("whatsapp.baileys.serviceDirectory"));
        ProcessBuilder process = new ProcessBuilder("bash", directory.resolve("start-baileys.sh").toString());
        process.directory(directory.toFile());
        process.environment().remove("BAILEYS_RESET_SESSION");
        process.redirectOutput(ProcessBuilder.Redirect.appendTo(directory.resolve("baileys-service.log").toFile()));
        process.redirectErrorStream(true);
        process.start();
    }

    private static void stop() throws IOException {
        HttpURLConnection connection = openGateway("/api/shutdown", "POST");
        try {
            connection.setDoOutput(true);
            try (var output = connection.getOutputStream()) {
                output.write(new byte[0]);
            }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300)
                throw new IOException("gateway_shutdown_failed");
        } finally {
            connection.disconnect();
        }
    }

    private static boolean gatewayReachable() {
        HttpURLConnection connection = null;
        try {
            connection = openGateway("/api/status", "GET");
            return connection.getResponseCode() == 200;
        } catch (IOException exception) {
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static HttpURLConnection openGateway(String path, String method) throws IOException {
        String base = AppProperties.get("whatsapp.baileys.gatewayBaseUrl", "http://127.0.0.1:3001").replaceAll("/+$", "");
        int timeout = Math.max(1, AppProperties.getInt("whatsapp.baileys.gatewayTimeoutMs", 4000));
        HttpURLConnection connection = (HttpURLConnection) new URL(base + path).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(timeout);
        connection.setReadTimeout(timeout);
        return connection;
    }
}
