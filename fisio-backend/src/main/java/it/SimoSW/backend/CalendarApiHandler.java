package it.SimoSW.backend;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import it.SimoSW.controller.application.CalendarController;
import it.SimoSW.exception.AppointmentNotFoundException;
import it.SimoSW.exception.InvalidAppointmentStateException;
import it.SimoSW.exception.TimeSlotNotAvailableException;
import it.SimoSW.model.Appointment;
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
            ApiJson.allowLocalFileOrigin(exchange, "GET, POST, PUT, DELETE, OPTIONS", "Authorization, Content-Type");
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            String method = exchange.getRequestMethod();
            if (!List.of("GET", "POST", "PUT", "DELETE").contains(method)) {
                exchange.getResponseHeaders().set("Allow", "GET, POST, PUT, DELETE");
                ApiJson.send(exchange, 405, "{\"error\":\"method_not_allowed\"}");
                return;
            }

            User therapist = authenticator.authenticate(exchange);
            if (therapist == null) {
                ApiJson.send(exchange, 401, "{\"error\":\"unauthorized\"}");
                return;
            }
            if ("/api/me".equals(exchange.getRequestURI().getPath())) {
                if (!"GET".equals(method)) {
                    exchange.getResponseHeaders().set("Allow", "GET");
                    ApiJson.send(exchange, 405, "{\"error\":\"method_not_allowed\"}");
                    return;
                }
                ApiJson.send(exchange, 200, "{\"username\":" + ApiJson.quote(therapist.getUsername()) + ",\"role\":\"THERAPIST\"}");
                return;
            }
            String path = exchange.getRequestURI().getPath();
            if (!"/api/calendar".equals(path) && !path.startsWith("/api/calendar/")) {
                ApiJson.send(exchange, 404, "{\"error\":\"not_found\"}");
                return;
            }
            Long therapistId = authenticator.therapistId(therapist);
            if (therapistId == null) {
                ApiJson.send(exchange, 401, "{\"error\":\"unauthorized\"}");
                return;
            }
            if (path.startsWith("/api/calendar/")) {
                long appointmentId;
                try {
                    appointmentId = Long.parseLong(path.substring("/api/calendar/".length()));
                    if (appointmentId <= 0) throw new NumberFormatException();
                } catch (NumberFormatException exception) {
                    ApiJson.send(exchange, 400, "{\"error\":\"invalid_id\"}");
                    return;
                }
                if ("PUT".equals(method) || "DELETE".equals(method)) {
                    try {
                        calendar.getAppointmentForTherapist(appointmentId, therapistId);
                    } catch (IllegalArgumentException exception) {
                        ApiJson.send(exchange, 404, "{\"error\":\"not_found\"}");
                        return;
                    }
                }
                if ("PUT".equals(method)) update(exchange, therapistId, appointmentId);
                else if ("DELETE".equals(method)) {
                    calendar.cancelAppointment(appointmentId, therapistId);
                    exchange.sendResponseHeaders(204, -1);
                } else {
                    exchange.getResponseHeaders().set("Allow", "PUT, DELETE");
                    ApiJson.send(exchange, 405, "{\"error\":\"method_not_allowed\"}");
                }
                return;
            }
            if ("POST".equals(method)) {
                create(exchange, therapistId);
                return;
            }
            if (!"GET".equals(method)) {
                exchange.getResponseHeaders().set("Allow", "GET, POST");
                ApiJson.send(exchange, 405, "{\"error\":\"method_not_allowed\"}");
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
        } catch (AppointmentNotFoundException exception) {
            ApiJson.send(exchange, 404, "{\"error\":\"not_found\"}");
        } catch (InvalidAppointmentStateException exception) {
            ApiJson.send(exchange, 409, "{\"error\":\"invalid_state\"}");
        } catch (TimeSlotNotAvailableException exception) {
            ApiJson.send(exchange, 409, "{\"error\":\"time_slot_unavailable\"}");
        } catch (IllegalArgumentException exception) {
            ApiJson.send(exchange, 400, "{\"error\":\"invalid_input\"}");
        } catch (RuntimeException exception) {
            System.err.println("Errore API calendario: " + exception.getClass().getSimpleName());
            ApiJson.send(exchange, 503, "{\"error\":\"unavailable\"}");
        } finally {
            exchange.close();
        }
    }

    private void update(HttpExchange exchange, long therapistId, long appointmentId) throws IOException {
        Map<String, String> fields = readForm(exchange);
        if (fields == null) return;
        String name = fields.get("patientName");
        String notes = fields.get("notes");
        if (name == null || name.isBlank() || name.length() > 180 || notes != null && notes.length() > 3000) {
            ApiJson.send(exchange, 400, "{\"error\":\"invalid_input\"}");
            return;
        }
        LocalDateTime start = parseDateTime(fields.get("start"));
        LocalDateTime end = parseDateTime(fields.get("end"));
        if (start == null || end == null) {
            ApiJson.send(exchange, 400, "{\"error\":\"invalid_period\"}");
            return;
        }
        calendar.rescheduleAppointment(appointmentId, therapistId, name, start, end,
                Boolean.parseBoolean(fields.get("allDay")), Boolean.parseBoolean(fields.get("nonTreatmentEvent")), notes);
        exchange.sendResponseHeaders(204, -1);
    }

    private void create(HttpExchange exchange, long therapistId) throws IOException {
        Map<String, String> fields = readForm(exchange);
        if (fields == null) return;
        String name = fields.get("patientName");
        String phone = fields.get("patientPhone");
        String notes = fields.get("notes");
        if (name == null || name.isBlank() || name.length() > 180
                || phone != null && phone.length() > 20 || notes != null && notes.length() > 3000) {
            ApiJson.send(exchange, 400, "{\"error\":\"invalid_input\"}");
            return;
        }
        boolean allDay = Boolean.parseBoolean(fields.get("allDay"));
        boolean generic = Boolean.parseBoolean(fields.get("nonTreatmentEvent"));
        LocalDateTime start = parseDateTime(fields.get("start"));
        LocalDateTime end = parseDateTime(fields.get("end"));
        if (start == null || end == null) {
            ApiJson.send(exchange, 400, "{\"error\":\"invalid_period\"}");
            return;
        }
        Long patientId = generic ? null : allDay
                ? calendar.resolveExistingPatientId(name, therapistId)
                : calendar.resolveOrCreatePatientId(name, phone, therapistId);
        Appointment appointment = new Appointment();
        appointment.setTherapistId(therapistId);
        appointment.setPatientId(patientId);
        appointment.setTitle(generic ? name.trim() : null);
        appointment.setStart(start);
        appointment.setEnd(end);
        appointment.setAllDay(allDay);
        appointment.setNotes(notes == null || notes.isBlank() ? null : notes.trim());
        Appointment saved = calendar.scheduleAppointment(appointment);
        ApiJson.send(exchange, 201, "{\"id\":" + saved.getId() + "}");
    }

    private static Map<String, String> readForm(HttpExchange exchange) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.startsWith("application/x-www-form-urlencoded")) {
            ApiJson.send(exchange, 415, "{\"error\":\"unsupported_media_type\"}");
            return null;
        }
        byte[] body = exchange.getRequestBody().readNBytes(4097);
        if (body.length > 4096) {
            ApiJson.send(exchange, 413, "{\"error\":\"body_too_large\"}");
            return null;
        }
        return parseQuery(new String(body, StandardCharsets.UTF_8));
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
                    .append(",\"patientPhone\":").append(ApiJson.quote(event.getPatientPhone()))
                    .append(",\"notes\":").append(ApiJson.quote(event.getNotes()))
                    .append("}}");
        }
        return json.append(']').toString();
    }

}
