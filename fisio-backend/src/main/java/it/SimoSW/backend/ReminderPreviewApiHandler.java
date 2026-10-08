package it.SimoSW.backend;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import it.SimoSW.controller.application.CalendarController;
import it.SimoSW.model.Appointment;
import it.SimoSW.model.User;
import it.SimoSW.model.dao.ReminderTemplateDAO;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

final class ReminderPreviewApiHandler implements HttpHandler {
    static final String DEFAULT_TEMPLATE = "Le ricordiamo l'appuntamento fissato per {giorno} per l'orario {ora inizio - ora fine}.";
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ITALIAN);
    private static final DateTimeFormatter TIME_LABEL = DateTimeFormatter.ofPattern("HH:mm");

    private final TherapistAuthenticator authenticator;
    private final CalendarController calendar;
    private final ReminderTemplateDAO templates;

    ReminderPreviewApiHandler(TherapistAuthenticator authenticator, CalendarController calendar, ReminderTemplateDAO templates) {
        this.authenticator = authenticator;
        this.calendar = calendar;
        this.templates = templates;
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
            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            LocalDate date;
            try {
                date = LocalDate.parse(query.get("date"));
            } catch (DateTimeParseException | NullPointerException exception) {
                ApiJson.send(exchange, 400, "{\"error\":\"invalid_date\"}");
                return;
            }
            String template = templates.findTemplateByTherapistId(therapistId)
                    .filter(value -> !value.isBlank()).orElse(DEFAULT_TEMPLATE);
            String dayLabel = date.format(DAY_LABEL);
            List<Appointment> appointments = calendar.getReminderCandidatesForTherapistInPeriod(
                    therapistId, date.atStartOfDay(), date.plusDays(1).atStartOfDay());
            StringBuilder recipients = new StringBuilder("[");
            for (Appointment appointment : appointments) {
                if (recipients.length() > 1) recipients.append(',');
                String patientName = calendar.resolvePatientFullName(appointment.getPatientId(), therapistId);
                String start = appointment.getStart().format(TIME_LABEL);
                String end = appointment.getEnd().format(TIME_LABEL);
                String range = start + " - " + end;
                String message = renderMessage(template, patientName, dayLabel, start, end);
                recipients.append("{\"appointmentId\":").append(appointment.getId())
                        .append(",\"patientName\":").append(ApiJson.quote(patientName))
                        .append(",\"patientPhone\":").append(ApiJson.quote(
                                calendar.resolvePatientPhone(appointment.getPatientId(), therapistId)))
                        .append(",\"timeRange\":").append(ApiJson.quote(range))
                        .append(",\"startTime\":").append(ApiJson.quote(start))
                        .append(",\"endTime\":").append(ApiJson.quote(end))
                        .append(",\"message\":").append(ApiJson.quote(message)).append('}');
            }
            recipients.append(']');
            ApiJson.send(exchange, 200, "{\"date\":" + ApiJson.quote(date.toString())
                    + ",\"dayLabel\":" + ApiJson.quote(dayLabel)
                    + ",\"template\":" + ApiJson.quote(template)
                    + ",\"defaultTemplate\":" + ApiJson.quote(DEFAULT_TEMPLATE)
                    + ",\"sendEnabled\":" + ReminderSendApiHandler.isConfigured(therapistId)
                    + ",\"recipients\":" + recipients + "}");
        } catch (RuntimeException exception) {
            System.err.println("Errore API anteprima promemoria: " + exception.getClass().getSimpleName());
            ApiJson.send(exchange, 503, "{\"error\":\"unavailable\"}");
        } finally {
            exchange.close();
        }
    }

    static String renderMessage(String template, String patientName, String dayLabel, String start, String end) {
        return template.replace("{nome paziente}", patientName).replace("{giorno}", dayLabel)
                .replace("{ora inizio}", start).replace("{ora fine}", end)
                .replace("{ora inizio - ora fine}", start + " - " + end);
    }

    static String normalizeTemplate(String template) {
        return template == null || template.isBlank() ? DEFAULT_TEMPLATE : template.trim();
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> values = new HashMap<>();
        if (raw != null) for (String part : raw.split("&")) {
            int separator = part.indexOf('=');
            if (separator > 0) values.put(URLDecoder.decode(part.substring(0, separator), StandardCharsets.UTF_8),
                    URLDecoder.decode(part.substring(separator + 1), StandardCharsets.UTF_8));
        }
        return values;
    }
}
