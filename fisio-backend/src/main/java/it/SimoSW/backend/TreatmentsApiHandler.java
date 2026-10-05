package it.SimoSW.backend;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import it.SimoSW.controller.application.CalendarController;
import it.SimoSW.controller.application.TreatmentController;
import it.SimoSW.controller.application.TreatmentController.TreatmentHistoryEntry;
import it.SimoSW.model.Appointment;
import it.SimoSW.model.AppointmentState;
import it.SimoSW.model.User;
import it.SimoSW.model.dao.PatientDAO;
import it.SimoSW.exception.AppointmentNotFoundException;
import it.SimoSW.exception.InvalidAppointmentStateException;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class TreatmentsApiHandler implements HttpHandler {
    private final TherapistAuthenticator authenticator;
    private final CalendarController calendar;
    private final TreatmentController treatments;
    private final PatientDAO patients;

    TreatmentsApiHandler(TherapistAuthenticator authenticator, CalendarController calendar,
                         TreatmentController treatments, PatientDAO patients) {
        this.authenticator = authenticator;
        this.calendar = calendar;
        this.treatments = treatments;
        this.patients = patients;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            ApiJson.allowLocalFileOrigin(exchange, "GET, POST, OPTIONS", "Authorization, Content-Type");
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            User user = authenticator.authenticate(exchange);
            Long therapistId = user == null ? null : authenticator.therapistId(user);
            if (therapistId == null) {
                ApiJson.send(exchange, 401, "{\"error\":\"unauthorized\"}");
                return;
            }
            String path = exchange.getRequestURI().getPath();
            if ("/api/treatments".equals(path) && "GET".equals(exchange.getRequestMethod())) {
                Map<String, String> query = parseFields(exchange.getRequestURI().getRawQuery());
                List<TreatmentHistoryEntry> history;
                if (query.containsKey("patientId")) {
                    long patientId = positiveId(query.get("patientId"));
                    try {
                        history = treatments.getTreatmentChronologyForPatient(therapistId, patientId);
                    } catch (IllegalArgumentException exception) {
                        ApiJson.send(exchange, 404, "{\"error\":\"not_found\"}");
                        return;
                    }
                } else {
                    history = treatments.getStartedHistoryForTherapistWithMultiSessionPlans(therapistId);
                }
                ApiJson.send(exchange, 200, historyJson(history));
                return;
            }
            if (path.startsWith("/api/treatments/appointments/") && "POST".equals(exchange.getRequestMethod())) {
                long appointmentId = positiveId(path.substring("/api/treatments/appointments/".length()));
                Appointment appointment;
                try {
                    appointment = calendar.getAppointmentForTherapist(appointmentId, therapistId);
                } catch (IllegalArgumentException | AppointmentNotFoundException exception) {
                    ApiJson.send(exchange, 404, "{\"error\":\"not_found\"}");
                    return;
                }
                if (appointment.getState() != AppointmentState.SCHEDULED || appointment.isAllDay()
                        || appointment.getPatientId() == null) {
                    ApiJson.send(exchange, 409, "{\"error\":\"invalid_state\"}");
                    return;
                }
                if (patients.findByIdForTherapist(appointment.getPatientId(), therapistId).isEmpty()) {
                    ApiJson.send(exchange, 409, "{\"error\":\"invalid_patient\"}");
                    return;
                }
                String type = exchange.getRequestHeaders().getFirst("Content-Type");
                if (type == null || !type.startsWith("application/x-www-form-urlencoded")) {
                    ApiJson.send(exchange, 415, "{\"error\":\"unsupported_media_type\"}");
                    return;
                }
                byte[] body = exchange.getRequestBody().readNBytes(16385);
                if (body.length > 16384) {
                    ApiJson.send(exchange, 413, "{\"error\":\"body_too_large\"}");
                    return;
                }
                Map<String, String> fields = parseFields(new String(body, StandardCharsets.UTF_8));
                String title = fields.get("planTitle");
                if (title == null || title.isBlank() || title.length() > 150) {
                    ApiJson.send(exchange, 400, "{\"error\":\"invalid_title\"}");
                    return;
                }
                Integer total = number(fields.get("totalSessionsPlanned"), 1, 65535);
                if (total == null) total = 1;
                Integer frequency = number(fields.get("frequencyPerWeek"), 1, 255);
                Integer pre = number(fields.get("painScorePre"), 0, 10);
                Integer post = number(fields.get("painScorePost"), 0, 10);
                if (fields.getOrDefault("sessionOutcome", "").length() > 255) {
                    ApiJson.send(exchange, 400, "{\"error\":\"invalid_input\"}");
                    return;
                }
                LocalDate end = fields.get("expectedEndDate") == null || fields.get("expectedEndDate").isBlank()
                        ? null : LocalDate.parse(fields.get("expectedEndDate"));
                calendar.completeAppointment(appointmentId, therapistId);
                treatments.createTreatmentForCompletedAppointment(appointmentId, title,
                        fields.get("goals"), frequency, end, total, pre, post,
                        fields.get("sessionOutcome"), fields.get("homeExercises"), fields.get("notes"));
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            exchange.getResponseHeaders().set("Allow", "/api/treatments".equals(path) ? "GET" : "POST");
            ApiJson.send(exchange, "/api/treatments".equals(path) || path.startsWith("/api/treatments/appointments/")
                    ? 405 : 404, "{\"error\":\"method_not_allowed\"}");
        } catch (IllegalArgumentException exception) {
            ApiJson.send(exchange, 400, "{\"error\":\"invalid_input\"}");
        } catch (IllegalStateException exception) {
            ApiJson.send(exchange, 409, "{\"error\":\"invalid_state\"}");
        } catch (InvalidAppointmentStateException exception) {
            ApiJson.send(exchange, 409, "{\"error\":\"invalid_state\"}");
        } catch (RuntimeException exception) {
            System.err.println("Errore API trattamenti: " + exception.getClass().getSimpleName());
            ApiJson.send(exchange, 503, "{\"error\":\"unavailable\"}");
        } finally {
            exchange.close();
        }
    }

    private static long positiveId(String value) {
        long id = Long.parseLong(value);
        if (id <= 0) throw new IllegalArgumentException("invalid_id");
        return id;
    }

    private static Integer number(String value, int min, int max) {
        if (value == null || value.isBlank()) return null;
        int parsed = Integer.parseInt(value);
        if (parsed < min || parsed > max) throw new IllegalArgumentException("invalid_number");
        return parsed;
    }

    private static Map<String, String> parseFields(String raw) {
        Map<String, String> fields = new HashMap<>();
        if (raw != null) for (String pair : raw.split("&")) {
            int separator = pair.indexOf('=');
            if (separator > 0) fields.put(URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8));
        }
        return fields;
    }

    private static String historyJson(List<TreatmentHistoryEntry> history) {
        StringBuilder json = new StringBuilder("[");
        for (TreatmentHistoryEntry entry : history) {
            if (json.length() > 1) json.append(',');
            json.append("{\"sessionId\":").append(entry.getSessionId())
                    .append(",\"patientId\":").append(entry.getPatientId())
                    .append(",\"patientName\":").append(ApiJson.quote(entry.getPatientName()))
                    .append(",\"planTitle\":").append(ApiJson.quote(entry.getPlanTitle()))
                    .append(",\"sessionStart\":").append(ApiJson.quote(entry.getSessionStart().toString()))
                    .append(",\"painScorePre\":").append(entry.getPainScorePre())
                    .append(",\"painScorePost\":").append(entry.getPainScorePost())
                    .append(",\"outcome\":").append(ApiJson.quote(entry.getOutcome()))
                    .append(",\"state\":").append(ApiJson.quote(entry.getStateLabel())).append('}');
        }
        return json.append(']').toString();
    }
}
