package it.SimoSW.backend;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import it.SimoSW.model.User;

import java.io.IOException;

final class RememberApiHandler implements HttpHandler {
    private final TherapistAuthenticator authenticator;

    RememberApiHandler(TherapistAuthenticator authenticator) {
        this.authenticator = authenticator;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            ApiJson.allowLocalFileOrigin(exchange, "POST, DELETE, OPTIONS", "Authorization");
            String method = exchange.getRequestMethod();
            if ("OPTIONS".equals(method)) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"/api/auth/remember".equals(exchange.getRequestURI().getPath())) {
                ApiJson.send(exchange, 404, "{\"error\":\"not_found\"}");
                return;
            }
            if ("POST".equals(method)) {
                User therapist = authenticator.authenticateBasic(exchange);
                if (therapist == null) {
                    ApiJson.send(exchange, 401, "{\"error\":\"unauthorized\"}");
                    return;
                }
                String token = authenticator.createRememberToken(therapist);
                ApiJson.send(exchange, 201, "{\"token\":" + ApiJson.quote(token) + "}");
            } else if ("DELETE".equals(method)) {
                String authorization = exchange.getRequestHeaders().getFirst("Authorization");
                if (authorization == null || !authorization.startsWith("Bearer ")
                        || !authorization.substring(7).matches("[A-Za-z0-9_-]{43}")) {
                    ApiJson.send(exchange, 401, "{\"error\":\"unauthorized\"}");
                    return;
                }
                authenticator.revokeRememberToken(authorization.substring(7));
                exchange.sendResponseHeaders(204, -1);
            } else {
                exchange.getResponseHeaders().set("Allow", "POST, DELETE");
                ApiJson.send(exchange, 405, "{\"error\":\"method_not_allowed\"}");
            }
        } catch (RuntimeException exception) {
            System.err.println("Errore API accesso automatico: " + exception.getClass().getSimpleName());
            ApiJson.send(exchange, 503, "{\"error\":\"unavailable\"}");
        } finally {
            exchange.close();
        }
    }
}
