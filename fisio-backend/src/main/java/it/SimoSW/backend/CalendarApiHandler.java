package it.SimoSW.backend;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import it.SimoSW.controller.application.CalendarController;
import it.SimoSW.model.CalendarEventView;
import it.SimoSW.model.User;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class CalendarApiHandler implements HttpHandler {
    private final TherapistAuthenticator authenticator;
    private final CalendarController calendar;

    CalendarApiHandler(TherapistAuthenticator authenticator, CalendarController calendar) {
        this.authenticator = authenticator;
        this.calendar = calendar;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            ApiJson.allowLocalFileOrigin(exchange, "GET, OPTIONS", "Authorization");
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "GET");
                ApiJson.send(exchange, 405, "{\"error\":\"method_not_allowed\"}");
                return;
            }

            User therapist = authenticator.authenticate(exchange);
            if (therapist == null) {
                ApiJson.send(exchange, 401, "{\"error\":\"unauthorized\"}");
                return;
            }
            if ("/api/me".equals(exchange.getRequestURI().getPath())) {
                ApiJson.send(exchange, 200, "{\"username\":" + ApiJson.quote(therapist.getUsername()) + ",\"role\":\"THERAPIST\"}");
                return;
            }
            if (!"/api/calendar".equals(exchange.getRequestURI().getPath())) {
                ApiJson.send(exchange, 404, "{\"error\":\"not_found\"}");
                return;
            }
            Long therapistId = authenticator.therapistId(therapist);
            if (therapistId == null) {
                ApiJson.send(exchange, 401, "{\"error\":\"unauthorized\"}");
                return;
            }

            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            LocalDateTime start = parseDateTime(query.get("start"));
            LocalDateTime end = parseDateTime(query.get("end"));
            if (start == null || end == null || !start.isBefore(end)
                    || Duration.between(start, end).toDays() > 62) {
                ApiJson.send(exchange, 400, "{\"error\":\"invalid_period\"}");
                return;
            }

            List<CalendarEventView> events = calendar.getCalendarEventViewsForTherapistInPeriod(therapistId, start, end);
            ApiJson.send(exchange, 200, toJson(events));
        } catch (RuntimeException exception) {
            System.err.println("Errore API calendario: " + exception.getClass().getSimpleName());
            ApiJson.send(exchange, 503, "{\"error\":\"unavailable\"}");
        } finally {
            exchange.close();
        }
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> values = new HashMap<>();
        if (rawQuery != null) {
            for (String part : rawQuery.split("&")) {
                int separator = part.indexOf('=');
                if (separator > 0) {
                    values.put(URLDecoder.decode(part.substring(0, separator), StandardCharsets.UTF_8),
                            URLDecoder.decode(part.substring(separator + 1), StandardCharsets.UTF_8));
                }
            }
        }
        return values;
    }

    private static LocalDateTime parseDateTime(String value) {
        if (value == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (DateTimeParseException exception) {
            try {
                return LocalDateTime.parse(value);
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
    }

    private static String toJson(List<CalendarEventView> events) {
        StringBuilder json = new StringBuilder("[");
        for (CalendarEventView event : events) {
            if (json.length() > 1) {
                json.append(',');
            }
            json.append("{\"id\":").append(event.getAppointmentId())
                    .append(",\"title\":").append(ApiJson.quote(event.getPatientFullName()))
                    .append(",\"start\":").append(ApiJson.quote(event.getStart().toString()))
                    .append(",\"end\":").append(ApiJson.quote(event.getEnd().toString()))
                    .append(",\"allDay\":").append(event.isAllDay())
                    .append(",\"extendedProps\":{\"patientId\":")
                    .append(event.getPatientId() == null ? "null" : event.getPatientId())
                    .append(",\"nonTreatmentEvent\":").append(event.getPatientId() == null)
                    .append(",\"state\":").append(ApiJson.quote(event.getState().name()))
                    .append(",\"notes\":").append(ApiJson.quote(event.getNotes()))
                    .append("}}");
        }
        return json.append(']').toString();
    }

}
