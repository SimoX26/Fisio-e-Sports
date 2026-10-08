package it.SimoSW.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import it.SimoSW.model.User;
import it.SimoSW.util.AppProperties;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class WhatsAppStatusApiHandler implements HttpHandler {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern QR_IMAGE = Pattern.compile("src=\"(data:image/png;base64,[A-Za-z0-9+/=]+)\"");
    private final TherapistAuthenticator authenticator;

    WhatsAppStatusApiHandler(TherapistAuthenticator authenticator) {
        this.authenticator = authenticator;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            ApiJson.allowLocalFileOrigin(exchange, "GET, OPTIONS", "Authorization, Content-Type");
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "GET");
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
                ApiJson.send(exchange, 200, "{\"configured\":false,\"managementMode\":\"systemd\",\"controlAvailable\":false,\"reachable\":false,\"ready\":false,\"qrRequired\":false,\"state\":\"DISABLED\",\"qrDataUrl\":null}");
                return;
            }
            String controlAvailable = ",\"managementMode\":"
                    + ApiJson.quote(AppProperties.get("whatsapp.baileys.managementMode", "systemd"))
                    + ",\"controlAvailable\":" + WhatsAppControlApiHandler.isAvailable();
            try {
                JsonNode status = JSON.readTree(request("/api/status", 65536));
                if (status == null || !status.isObject()) throw new IOException("invalid_status");
                boolean ready = status.path("ready").asBoolean(false);
                boolean qrRequired = status.path("qrRequired").asBoolean(false);
                String qrDataUrl = null;
                if (!ready && qrRequired) {
                    try {
                        Matcher image = QR_IMAGE.matcher(new String(request("/api/qr", 1048576), StandardCharsets.UTF_8));
                        if (image.find()) qrDataUrl = image.group(1);
                    } catch (IOException ignored) {
                        // Il gateway può essere raggiungibile mentre il QR si sta aggiornando.
                    }
                }
                ApiJson.send(exchange, 200, "{\"configured\":true" + controlAvailable + ",\"reachable\":true,\"ready\":" + ready
                        + ",\"qrRequired\":" + qrRequired
                        + ",\"state\":" + ApiJson.quote(status.path("state").asText("UNKNOWN"))
                        + ",\"lastError\":" + ApiJson.quote(status.path("lastError").asText(null))
                        + ",\"qrDataUrl\":" + ApiJson.quote(qrDataUrl) + "}");
            } catch (IOException exception) {
                ApiJson.send(exchange, 200, "{\"configured\":true" + controlAvailable + ",\"reachable\":false,\"ready\":false,\"qrRequired\":false,\"state\":\"OFFLINE\",\"qrDataUrl\":null}");
            }
        } catch (RuntimeException exception) {
            System.err.println("Errore API stato WhatsApp: " + exception.getClass().getSimpleName());
            ApiJson.send(exchange, 503, "{\"error\":\"unavailable\"}");
        } finally {
            exchange.close();
        }
    }

    private static byte[] request(String path, int maxBytes) throws IOException {
        String base = AppProperties.get("whatsapp.baileys.gatewayBaseUrl", "http://127.0.0.1:3001").replaceAll("/+$", "");
        int timeout = Math.max(1, AppProperties.getInt("whatsapp.baileys.gatewayTimeoutMs", 4000));
        HttpURLConnection connection = (HttpURLConnection) new URL(base + path).openConnection();
        try {
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);
            if (connection.getResponseCode() != 200) throw new IOException("gateway_unavailable");
            try (var input = connection.getInputStream()) {
                byte[] response = input.readNBytes(maxBytes + 1);
                if (response.length > maxBytes) throw new IOException("gateway_response_too_large");
                return response;
            }
        } finally {
            connection.disconnect();
        }
    }
}
