package it.SimoSW.backend;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import it.SimoSW.controller.application.CalendarController;
import it.SimoSW.controller.application.CalendarController.CancelledAppointmentView;
import it.SimoSW.exception.AppointmentNotFoundException;
import it.SimoSW.exception.InvalidAppointmentStateException;
import it.SimoSW.exception.TimeSlotNotAvailableException;
import it.SimoSW.model.User;

import java.io.IOException;
import java.util.List;

final class CalendarTrashApiHandler implements HttpHandler {
    private final TherapistAuthenticator authenticator;
    private final CalendarController calendar;

    CalendarTrashApiHandler(TherapistAuthenticator authenticator, CalendarController calendar) {
        this.authenticator = authenticator;
        this.calendar = calendar;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            ApiJson.allowLocalFileOrigin(exchange, "GET, PUT, DELETE, OPTIONS", "Authorization, Content-Type");
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
            String method = exchange.getRequestMethod();
            if ("/api/calendar/trash".equals(path)) {
                if ("GET".equals(method)) {
                    calendar.purgeExpiredTrashForTherapist(therapistId);
                    List<CancelledAppointmentView> entries = calendar.getCancelledAppointmentsForTherapist(therapistId);
                    ApiJson.send(exchange, 200, toJson(entries));
                } else if ("DELETE".equals(method)) {
                    int count = calendar.emptyTrashForTherapist(therapistId);
                    ApiJson.send(exchange, 200, "{\"deleted\":" + count + "}");
                } else {
                    exchange.getResponseHeaders().set("Allow", "GET, DELETE");
                    ApiJson.send(exchange, 405, "{\"error\":\"method_not_allowed\"}");
                }
                return;
            }
            if (!path.startsWith("/api/calendar/trash/")) {
                ApiJson.send(exchange, 404, "{\"error\":\"not_found\"}");
                return;
            }
            long id;
            try {
                id = Long.parseLong(path.substring("/api/calendar/trash/".length()));
                if (id <= 0) throw new NumberFormatException();
            } catch (NumberFormatException exception) {
                ApiJson.send(exchange, 400, "{\"error\":\"invalid_id\"}");
                return;
            }
            if (!"PUT".equals(method) && !"DELETE".equals(method)) {
                exchange.getResponseHeaders().set("Allow", "PUT, DELETE");
                ApiJson.send(exchange, 405, "{\"error\":\"method_not_allowed\"}");
                return;
            }
            try {
                calendar.getAppointmentForTherapist(id, therapistId);
            } catch (IllegalArgumentException | AppointmentNotFoundException exception) {
                ApiJson.send(exchange, 404, "{\"error\":\"not_found\"}");
                return;
            }
            if ("PUT".equals(method)) calendar.restoreAppointment(id, therapistId);
            else calendar.deleteCancelledAppointment(id, therapistId);
            exchange.sendResponseHeaders(204, -1);
        } catch (AppointmentNotFoundException exception) {
            ApiJson.send(exchange, 404, "{\"error\":\"not_found\"}");
        } catch (InvalidAppointmentStateException | TimeSlotNotAvailableException exception) {
            ApiJson.send(exchange, 409, "{\"error\":\"invalid_state_or_conflict\"}");
        } catch (RuntimeException exception) {
            System.err.println("Errore API cestino: " + exception.getClass().getSimpleName());
            ApiJson.send(exchange, 503, "{\"error\":\"unavailable\"}");
        } finally {
            exchange.close();
        }
    }

    private static String toJson(List<CancelledAppointmentView> entries) {
        StringBuilder json = new StringBuilder("[");
        for (CancelledAppointmentView entry : entries) {
            if (json.length() > 1) json.append(',');
            json.append("{\"id\":").append(entry.getId())
                    .append(",\"patientFullName\":").append(ApiJson.quote(entry.getPatientFullName()))
                    .append(",\"startLabel\":").append(ApiJson.quote(entry.getStartLabel()))
                    .append(",\"endLabel\":").append(ApiJson.quote(entry.getEndLabel()))
                    .append(",\"notes\":").append(ApiJson.quote(entry.getNotes())).append('}');
        }
        return json.append(']').toString();
    }
}
