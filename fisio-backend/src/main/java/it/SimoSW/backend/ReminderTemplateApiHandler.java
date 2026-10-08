package it.SimoSW.backend;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import it.SimoSW.model.User;
import it.SimoSW.model.dao.ReminderTemplateDAO;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

final class ReminderTemplateApiHandler implements HttpHandler {
    private final TherapistAuthenticator authenticator;
    private final ReminderTemplateDAO templates;

    ReminderTemplateApiHandler(TherapistAuthenticator authenticator, ReminderTemplateDAO templates) {
        this.authenticator = authenticator;
        this.templates = templates;
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
            byte[] body = exchange.getRequestBody().readNBytes(16385);
            if (body.length > 16384) {
                ApiJson.send(exchange, 413, "{\"error\":\"request_too_large\"}");
                return;
            }
            Map<String, List<String>> form = ReminderSendApiHandler.parseForm(new String(body, StandardCharsets.UTF_8));
            List<String> values = form.get("template");
            if (values == null || values.size() != 1) {
                ApiJson.send(exchange, 400, "{\"error\":\"invalid_template\"}");
                return;
            }
            String template = ReminderPreviewApiHandler.normalizeTemplate(values.get(0));
            templates.saveTemplate(therapistId, template);
            ApiJson.send(exchange, 200, "{\"template\":" + ApiJson.quote(template) + "}");
        } catch (RuntimeException exception) {
            System.err.println("Errore API modello promemoria: " + exception.getClass().getSimpleName());
            ApiJson.send(exchange, 503, "{\"error\":\"unavailable\"}");
        } finally {
            exchange.close();
        }
    }
}
