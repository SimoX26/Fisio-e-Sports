package it.SimoSW.backend;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import it.SimoSW.controller.application.AccessRequestController;
import it.SimoSW.exception.InvalidUserDataException;
import it.SimoSW.exception.UsernameAlreadyExistsException;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

final class AccessRequestApiHandler implements HttpHandler {
    private final AccessRequestController accessRequests;

    AccessRequestApiHandler(AccessRequestController accessRequests) {
        this.accessRequests = accessRequests;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            ApiJson.allowLocalFileOrigin(exchange, "POST, OPTIONS", "Content-Type");
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"/api/access-requests".equals(exchange.getRequestURI().getPath())) {
                ApiJson.send(exchange, 404, "{\"error\":\"not_found\"}");
                return;
            }
            String origin = exchange.getRequestHeaders().getFirst("Origin");
            if (origin != null && !"null".equals(origin)) {
                ApiJson.send(exchange, 403, "{\"error\":\"forbidden_origin\"}");
                return;
            }
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "POST");
                ApiJson.send(exchange, 405, "{\"error\":\"method_not_allowed\"}");
                return;
            }
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
            accessRequests.submitAccessRequest(fields.get("firstName"), fields.get("lastName"),
                    fields.get("email"), fields.get("username"), fields.get("password"));
            ApiJson.send(exchange, 201, "{\"status\":\"pending\"}");
        } catch (UsernameAlreadyExistsException exception) {
            ApiJson.send(exchange, 409, "{\"error\":\"username_exists\"}");
        } catch (InvalidUserDataException exception) {
            ApiJson.send(exchange, 400, "{\"error\":\"invalid_input\",\"message\":"
                    + ApiJson.quote(exception.getMessage()) + "}");
        } catch (IllegalArgumentException exception) {
            ApiJson.send(exchange, 400, "{\"error\":\"invalid_input\"}");
        } catch (RuntimeException exception) {
            System.err.println("Errore API richiesta accesso: " + exception.getClass().getSimpleName());
            ApiJson.send(exchange, 503, "{\"error\":\"unavailable\"}");
        } finally {
            exchange.close();
        }
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
}
