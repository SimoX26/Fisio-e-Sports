package it.SimoSW.backend;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import it.SimoSW.controller.application.AuthenticationController;
import it.SimoSW.controller.application.CalendarController;
import it.SimoSW.exception.AuthenticationFailedException;
import it.SimoSW.model.CalendarEventView;
import it.SimoSW.model.User;
import it.SimoSW.model.UserRole;
import it.SimoSW.model.dao.UserDAO;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class CalendarApiHandler implements HttpHandler {
    private final AuthenticationController authentication;
    private final CalendarController calendar;
    private final UserDAO users;

    CalendarApiHandler(AuthenticationController authentication, CalendarController calendar, UserDAO users) {
        this.authentication = authentication;
        this.calendar = calendar;
        this.users = users;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.getResponseHeaders().set("Vary", "Origin");
            if ("null".equals(exchange.getRequestHeaders().getFirst("Origin"))) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "null");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Authorization");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
            }
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "GET");
                send(exchange, 405, "{\"error\":\"method_not_allowed\"}");
                return;
            }

            User therapist = authenticateTherapist(exchange);
            if (therapist == null) {
                send(exchange, 401, "{\"error\":\"unauthorized\"}");
                return;
            }
            if ("/api/me".equals(exchange.getRequestURI().getPath())) {
                send(exchange, 200, "{\"username\":" + jsonString(therapist.getUsername()) + ",\"role\":\"THERAPIST\"}");
                return;
            }
            if (!"/api/calendar".equals(exchange.getRequestURI().getPath())) {
                send(exchange, 404, "{\"error\":\"not_found\"}");
                return;
            }
            Long therapistId = users.findIdByUsernameAndRole(therapist.getUsername(), UserRole.THERAPIST).orElse(null);
            if (therapistId == null) {
                send(exchange, 401, "{\"error\":\"unauthorized\"}");
                return;
            }

            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            LocalDateTime start = parseDateTime(query.get("start"));
            LocalDateTime end = parseDateTime(query.get("end"));
            if (start == null || end == null || !start.isBefore(end)
                    || Duration.between(start, end).toDays() > 62) {
                send(exchange, 400, "{\"error\":\"invalid_period\"}");
                return;
            }

            List<CalendarEventView> events = calendar.getCalendarEventViewsForTherapistInPeriod(therapistId, start, end);
            send(exchange, 200, toJson(events));
        } catch (RuntimeException exception) {
            System.err.println("Errore API calendario: " + exception.getClass().getSimpleName());
            send(exchange, 503, "{\"error\":\"unavailable\"}");
        } finally {
            exchange.close();
        }
    }

    private User authenticateTherapist(HttpExchange exchange) {
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (authorization == null || !authorization.startsWith("Basic ")) {
            return null;
        }
        try {
            String pair = new String(Base64.getDecoder().decode(authorization.substring(6)), StandardCharsets.UTF_8);
            int separator = pair.indexOf(':');
            if (separator <= 0) {
                return null;
            }
            User user = authentication.authenticate(pair.substring(0, separator), pair.substring(separator + 1));
            if (user.getRole() != UserRole.THERAPIST) {
                return null;
            }
            return user;
        } catch (IllegalArgumentException | AuthenticationFailedException exception) {
            return null;
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
                    .append(",\"title\":").append(jsonString(event.getPatientFullName()))
                    .append(",\"start\":").append(jsonString(event.getStart().toString()))
                    .append(",\"end\":").append(jsonString(event.getEnd().toString()))
                    .append(",\"allDay\":").append(event.isAllDay())
                    .append(",\"extendedProps\":{\"patientId\":")
                    .append(event.getPatientId() == null ? "null" : event.getPatientId())
                    .append(",\"nonTreatmentEvent\":").append(event.getPatientId() == null)
                    .append(",\"state\":").append(jsonString(event.getState().name()))
                    .append(",\"notes\":").append(jsonString(event.getNotes()))
                    .append("}}");
        }
        return json.append(']').toString();
    }

    private static String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder quoted = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> quoted.append("\\\"");
                case '\\' -> quoted.append("\\\\");
                case '\n' -> quoted.append("\\n");
                case '\r' -> quoted.append("\\r");
                case '\t' -> quoted.append("\\t");
                default -> {
                    if (character < 0x20) {
                        quoted.append(String.format("\\u%04x", (int) character));
                    } else {
                        quoted.append(character);
                    }
                }
            }
        }
        return quoted.append('"').toString();
    }

    private static void send(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}
