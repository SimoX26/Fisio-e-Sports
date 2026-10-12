package it.SimoSW.backend;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import it.SimoSW.controller.application.AccessRequestController;
import it.SimoSW.controller.application.AuthenticationController;
import it.SimoSW.exception.AuthenticationFailedException;
import it.SimoSW.exception.UsernameAlreadyExistsException;
import it.SimoSW.model.AccessRequest;
import it.SimoSW.model.User;
import it.SimoSW.model.UserRole;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

final class AdminApiHandler implements HttpHandler {
    private final AuthenticationController authentication;
    private final AccessRequestController accessRequests;

    AdminApiHandler(AuthenticationController authentication, AccessRequestController accessRequests) {
        this.authentication = authentication;
        this.accessRequests = accessRequests;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            ApiJson.allowLocalFileOrigin(exchange, "GET, POST, OPTIONS", "Authorization, Content-Type");
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            if ("/api/admin/auth/remember".equals(path)) {
                User admin = authenticateBasic(exchange);
                if (admin == null) {
                    ApiJson.send(exchange, 401, "{\"error\":\"unauthorized\"}");
                    return;
                }
                if (!"POST".equals(method)) {
                    methodNotAllowed(exchange, "POST");
                    return;
                }
                ApiJson.send(exchange, 201, "{\"token\":"
                        + ApiJson.quote(authentication.createRememberMeToken(admin)) + "}");
                return;
            }
            User admin = authenticate(exchange);
            if (admin == null) {
                ApiJson.send(exchange, 401, "{\"error\":\"unauthorized\"}");
                return;
            }
            if ("/api/admin/me".equals(path)) {
                if (!"GET".equals(method)) methodNotAllowed(exchange, "GET");
                else ApiJson.send(exchange, 200, "{\"username\":" + ApiJson.quote(admin.getUsername())
                        + ",\"role\":\"ADMIN\"}");
                return;
            }
            if ("/api/admin/access-requests".equals(path)) {
                if (!"GET".equals(method)) methodNotAllowed(exchange, "GET");
                else ApiJson.send(exchange, 200, "{\"pending\":" + requestsJson(accessRequests.getPendingRequests())
                        + ",\"recent\":" + requestsJson(accessRequests.getRecentRequests()) + "}");
                return;
            }
            String prefix = "/api/admin/access-requests/";
            if (path.startsWith(prefix) && path.endsWith("/review")) {
                if (!"POST".equals(method)) {
                    methodNotAllowed(exchange, "POST");
                    return;
                }
                long id;
                try {
                    id = Long.parseLong(path.substring(prefix.length(), path.length() - "/review".length()));
                    if (id <= 0) throw new NumberFormatException();
                } catch (NumberFormatException exception) {
                    ApiJson.send(exchange, 400, "{\"error\":\"invalid_id\"}");
                    return;
                }
                String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
                if (contentType == null || !contentType.startsWith("application/x-www-form-urlencoded")) {
                    ApiJson.send(exchange, 415, "{\"error\":\"unsupported_media_type\"}");
                    return;
                }
                byte[] body = exchange.getRequestBody().readNBytes(129);
                if (body.length > 128) {
                    ApiJson.send(exchange, 413, "{\"error\":\"body_too_large\"}");
                    return;
                }
                String action = new String(body, StandardCharsets.UTF_8);
                if (action.startsWith("action=")) action = URLDecoder.decode(action.substring(7), StandardCharsets.UTF_8);
                if ("approve".equals(action)) accessRequests.approve(id, admin.getUsername());
                else if ("reject".equals(action)) accessRequests.reject(id, admin.getUsername());
                else {
                    ApiJson.send(exchange, 400, "{\"error\":\"invalid_action\"}");
                    return;
                }
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            ApiJson.send(exchange, 404, "{\"error\":\"not_found\"}");
        } catch (UsernameAlreadyExistsException exception) {
            ApiJson.send(exchange, 409, "{\"error\":\"username_exists\"}");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            ApiJson.send(exchange, 409, "{\"error\":\"request_not_pending\"}");
        } catch (RuntimeException exception) {
            System.err.println("Errore API amministrazione: " + exception.getClass().getSimpleName());
            ApiJson.send(exchange, 503, "{\"error\":\"unavailable\"}");
        } finally {
            exchange.close();
        }
    }

    private User authenticate(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (!token.matches("[A-Za-z0-9_-]{43}")) return null;
            return authentication.authenticateByRememberMeToken(token)
                    .filter(user -> user.getRole() == UserRole.ADMIN).orElse(null);
        }
        return authenticateBasic(exchange);
    }

    private User authenticateBasic(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Basic ")) return null;
        try {
            String pair = new String(Base64.getDecoder().decode(header.substring(6)), StandardCharsets.UTF_8);
            int separator = pair.indexOf(':');
            if (separator <= 0) return null;
            User user = authentication.authenticate(pair.substring(0, separator), pair.substring(separator + 1));
            return user.getRole() == UserRole.ADMIN ? user : null;
        } catch (IllegalArgumentException | AuthenticationFailedException exception) {
            return null;
        }
    }

    private static void methodNotAllowed(HttpExchange exchange, String allow) throws IOException {
        exchange.getResponseHeaders().set("Allow", allow);
        ApiJson.send(exchange, 405, "{\"error\":\"method_not_allowed\"}");
    }

    private static String requestsJson(List<AccessRequest> requests) {
        StringBuilder json = new StringBuilder("[");
        for (AccessRequest request : requests) {
            if (json.length() > 1) json.append(',');
            json.append("{\"id\":").append(request.getId())
                    .append(",\"firstName\":").append(ApiJson.quote(request.getFirstName()))
                    .append(",\"lastName\":").append(ApiJson.quote(request.getLastName()))
                    .append(",\"email\":").append(ApiJson.quote(request.getEmail()))
                    .append(",\"username\":").append(ApiJson.quote(request.getUsername()))
                    .append(",\"requestedRole\":").append(ApiJson.quote(request.getRequestedRole().name()))
                    .append(",\"status\":").append(ApiJson.quote(request.getStatus().name()))
                    .append(",\"createdAt\":").append(ApiJson.quote(String.valueOf(request.getCreatedAt())))
                    .append(",\"reviewedAt\":").append(ApiJson.quote(request.getReviewedAt() == null
                            ? null : request.getReviewedAt().toString()))
                    .append('}');
        }
        return json.append(']').toString();
    }
}
