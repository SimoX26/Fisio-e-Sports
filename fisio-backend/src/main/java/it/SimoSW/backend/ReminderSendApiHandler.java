package it.SimoSW.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import it.SimoSW.controller.application.CalendarController;
import it.SimoSW.model.Appointment;
import it.SimoSW.model.User;
import it.SimoSW.model.dao.ReminderTemplateDAO;
import it.SimoSW.util.AppProperties;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class ReminderSendApiHandler implements HttpHandler {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", java.util.Locale.ITALIAN);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private final TherapistAuthenticator authenticator;
    private final CalendarController calendar;
    private final ReminderTemplateDAO templates;

    ReminderSendApiHandler(TherapistAuthenticator authenticator, CalendarController calendar, ReminderTemplateDAO templates) {
        this.authenticator = authenticator;
        this.calendar = calendar;
        this.templates = templates;
    }

    static boolean isConfigured(long therapistId) {
        if (!Boolean.parseBoolean(AppProperties.get("whatsapp.baileys.enabled", "false"))) return false;
        String allowed = AppProperties.get("whatsapp.baileys.therapistId");
        if (allowed == null || allowed.isBlank()) return true;
        try {
            return Long.parseLong(allowed.trim()) == therapistId;
        } catch (NumberFormatException exception) {
            return false;
        }
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
            if (!isConfigured(therapistId)) {
                ApiJson.send(exchange, 428, "{\"error\":\"whatsapp_not_configured\"}");
                return;
            }
            byte[] body = exchange.getRequestBody().readNBytes(16385);
            if (body.length > 16384) {
                ApiJson.send(exchange, 413, "{\"error\":\"request_too_large\"}");
                return;
            }
            Map<String, List<String>> form = parseForm(new String(body, StandardCharsets.UTF_8));
            LocalDate date;
            try {
                date = LocalDate.parse(form.getOrDefault("date", List.of()).get(0));
            } catch (DateTimeParseException | IndexOutOfBoundsException exception) {
                ApiJson.send(exchange, 400, "{\"error\":\"invalid_date\"}");
                return;
            }
            Set<Long> selected = new HashSet<>();
            try {
                for (String raw : form.getOrDefault("appointmentId", List.of())) {
                    long id = Long.parseLong(raw);
                    if (id <= 0 || !selected.add(id)) throw new NumberFormatException();
                }
            } catch (NumberFormatException exception) {
                ApiJson.send(exchange, 400, "{\"error\":\"invalid_appointment_ids\"}");
                return;
            }
            if (selected.isEmpty() || selected.size() > 100) {
                ApiJson.send(exchange, 400, "{\"error\":\"invalid_appointment_ids\"}");
                return;
            }
            List<Appointment> candidates = calendar.getReminderCandidatesForTherapistInPeriod(
                    therapistId, date.atStartOfDay(), date.plusDays(1).atStartOfDay());
            List<Appointment> recipients = candidates.stream().filter(item -> selected.contains(item.getId())).collect(Collectors.toList());
            if (recipients.size() != selected.size()) {
                ApiJson.send(exchange, 404, "{\"error\":\"appointment_not_found\"}");
                return;
            }
            String template;
            if (form.containsKey("template")) {
                template = ReminderPreviewApiHandler.normalizeTemplate(form.get("template").get(0));
                templates.saveTemplate(therapistId, template);
            } else {
                template = templates.findTemplateByTherapistId(therapistId)
                        .filter(value -> !value.isBlank()).orElse(ReminderPreviewApiHandler.DEFAULT_TEMPLATE);
            }
            int sent = 0;
            int skipped = 0;
            int failed = 0;
            for (Appointment appointment : recipients) {
                String phone = calendar.resolvePatientPhone(appointment.getPatientId(), therapistId);
                if (phone == null || phone.isBlank()) {
                    skipped++;
                    continue;
                }
                String name = calendar.resolvePatientFullName(appointment.getPatientId(), therapistId);
                String message = ReminderPreviewApiHandler.renderMessage(template, name, date.format(DAY),
                        appointment.getStart().format(TIME), appointment.getEnd().format(TIME));
                try {
                    sendMessage(phone, message);
                    sent++;
                } catch (IOException | RuntimeException exception) {
                    failed++;
                    System.err.println("Invio promemoria non riuscito: " + exception.getClass().getSimpleName());
                }
            }
            ApiJson.send(exchange, 200, "{\"processedCount\":" + recipients.size() + ",\"sentCount\":" + sent
                    + ",\"skippedCount\":" + skipped + ",\"failedCount\":" + failed + "}");
        } catch (RuntimeException exception) {
            System.err.println("Errore API invio promemoria: " + exception.getClass().getSimpleName());
            ApiJson.send(exchange, 503, "{\"error\":\"unavailable\"}");
        } finally {
            exchange.close();
        }
    }

    static Map<String, List<String>> parseForm(String body) {
        Map<String, List<String>> values = new HashMap<>();
        for (String part : body.split("&")) {
            int separator = part.indexOf('=');
            if (separator < 0) continue;
            String key = URLDecoder.decode(part.substring(0, separator), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(part.substring(separator + 1), StandardCharsets.UTF_8);
            values.computeIfAbsent(key, unused -> new ArrayList<>()).add(value);
        }
        return values;
    }

    private static void sendMessage(String rawPhone, String message) throws IOException {
        String phone = rawPhone.replaceAll("[^0-9]", "");
        if (phone.startsWith("00")) phone = phone.substring(2);
        if (phone.length() < 8 || phone.length() > 15 || message.isBlank()) {
            throw new IllegalArgumentException("invalid_recipient");
        }
        String baseUrl = AppProperties.get("whatsapp.baileys.gatewayBaseUrl", "http://127.0.0.1:3001").replaceAll("/+$", "");
        int timeout = Math.max(1, AppProperties.getInt("whatsapp.baileys.gatewayTimeoutMs", 4000));
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + "/api/send").openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);
            connection.setDoOutput(true);
            byte[] payload = ("{\"recipient\":" + ApiJson.quote(phone) + ",\"message\":" + ApiJson.quote(message.trim()) + "}")
                    .getBytes(StandardCharsets.UTF_8);
            try (var output = connection.getOutputStream()) {
                output.write(payload);
            }
            int status = connection.getResponseCode();
            var input = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
            JsonNode response;
            if (input == null) {
                response = null;
            } else {
                try (input) {
                    response = JSON.readTree(input);
                }
            }
            if (status < 200 || status >= 300 || response == null || !response.path("success").asBoolean(false)
                    || response.path("messageId").asText("").isBlank()) {
                throw new IOException("gateway_send_failed");
            }
        } finally {
            connection.disconnect();
        }
    }
}
