package it.SimoSW.backend;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import it.SimoSW.controller.application.WaitlistController;
import it.SimoSW.model.User;
import it.SimoSW.model.WaitlistEntry;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class WaitlistApiHandler implements HttpHandler {
    private final TherapistAuthenticator authenticator;
    private final WaitlistController waitlist;

    WaitlistApiHandler(TherapistAuthenticator authenticator, WaitlistController waitlist) {
        this.authenticator = authenticator;
        this.waitlist = waitlist;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            ApiJson.allowLocalFileOrigin(exchange, "GET, POST, DELETE, OPTIONS", "Authorization, Content-Type");
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            User therapist = authenticator.authenticate(exchange);
            Long therapistId = therapist == null ? null : authenticator.therapistId(therapist);
            if (therapistId == null) {
                ApiJson.send(exchange, 401, "{\"error\":\"unauthorized\"}");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            if ("/api/waitlist".equals(path) && "GET".equals(method)) {
                ApiJson.send(exchange, 200, toJson(waitlist.getEntriesForTherapist(therapistId)));
            } else if ("/api/waitlist".equals(path) && "POST".equals(method)) {
                create(exchange, therapistId);
            } else if (path.startsWith("/api/waitlist/") && "DELETE".equals(method)) {
                remove(exchange, therapistId, path.substring("/api/waitlist/".length()));
            } else {
                exchange.getResponseHeaders().set("Allow", "GET, POST, DELETE");
                ApiJson.send(exchange, 405, "{\"error\":\"method_not_allowed\"}");
            }
        } catch (IllegalArgumentException exception) {
            ApiJson.send(exchange, 400, "{\"error\":\"invalid_input\"}");
        } catch (RuntimeException exception) {
            System.err.println("Errore API lista di attesa: " + exception.getClass().getSimpleName());
            ApiJson.send(exchange, 503, "{\"error\":\"unavailable\"}");
        } finally {
            exchange.close();
        }
    }

    private void create(HttpExchange exchange, long therapistId) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.startsWith("application/x-www-form-urlencoded")) {
            ApiJson.send(exchange, 415, "{\"error\":\"unsupported_media_type\"}");
            return;
        }
        byte[] body = exchange.getRequestBody().readNBytes(4097);
        if (body.length > 4096) {
            ApiJson.send(exchange, 413, "{\"error\":\"body_too_large\"}");
            return;
        }
        Map<String, String> fields = parseForm(new String(body, StandardCharsets.UTF_8));
        String fullName = fields.get("fullName");
        String phone = fields.get("phone");
        if (fullName == null || fullName.isBlank() || fullName.length() > 180
                || phone == null || phone.isBlank() || phone.length() > 20) {
            ApiJson.send(exchange, 400, "{\"error\":\"invalid_input\"}");
            return;
        }
        WaitlistEntry entry = new WaitlistEntry();
        entry.setTherapistId(therapistId);
        entry.setFullName(fullName);
        entry.setPhone(phone);
        waitlist.addEntry(entry);
        ApiJson.send(exchange, 201, "{\"id\":" + entry.getId() + "}");
    }

    private void remove(HttpExchange exchange, long therapistId, String rawId) throws IOException {
        long id;
        try {
            id = Long.parseLong(rawId);
        } catch (NumberFormatException exception) {
            ApiJson.send(exchange, 400, "{\"error\":\"invalid_id\"}");
            return;
        }
        boolean owned = waitlist.getEntriesForTherapist(therapistId).stream().anyMatch(entry -> entry.getId() == id);
        if (!owned) {
            ApiJson.send(exchange, 404, "{\"error\":\"not_found\"}");
            return;
        }
        waitlist.removeEntry(id, therapistId);
        exchange.sendResponseHeaders(204, -1);
    }

    private static Map<String, String> parseForm(String body) {
        Map<String, String> fields = new HashMap<>();
        for (String part : body.split("&")) {
            int separator = part.indexOf('=');
            if (separator > 0) {
                fields.put(URLDecoder.decode(part.substring(0, separator), StandardCharsets.UTF_8),
                        URLDecoder.decode(part.substring(separator + 1), StandardCharsets.UTF_8));
            }
        }
        return fields;
    }

    private static String toJson(List<WaitlistEntry> entries) {
        StringBuilder json = new StringBuilder("[");
        for (WaitlistEntry entry : entries) {
            if (json.length() > 1) {
                json.append(',');
            }
            json.append("{\"id\":").append(entry.getId())
                    .append(",\"fullName\":").append(ApiJson.quote(entry.getFullName()))
                    .append(",\"phone\":").append(ApiJson.quote(entry.getPhone()))
                    .append(",\"createdAtLabel\":").append(ApiJson.quote(entry.getCreatedAtLabel()))
                    .append('}');
        }
        return json.append(']').toString();
    }
}
