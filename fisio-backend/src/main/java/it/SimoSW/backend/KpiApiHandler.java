package it.SimoSW.backend;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import it.SimoSW.controller.application.KpiSnapshotController;
import it.SimoSW.model.KpiMonthlySnapshot;
import it.SimoSW.model.User;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

final class KpiApiHandler implements HttpHandler {
    private final TherapistAuthenticator authenticator;
    private final KpiSnapshotController kpi;

    KpiApiHandler(TherapistAuthenticator authenticator, KpiSnapshotController kpi) {
        this.authenticator = authenticator;
        this.kpi = kpi;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            ApiJson.allowLocalFileOrigin(exchange, "GET, OPTIONS", "Authorization");
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"/api/kpi".equals(exchange.getRequestURI().getPath())) {
                ApiJson.send(exchange, 404, "{\"error\":\"not_found\"}");
                return;
            }
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "GET");
                ApiJson.send(exchange, 405, "{\"error\":\"method_not_allowed\"}");
                return;
            }
            User therapist = authenticator.authenticate(exchange);
            Long therapistId = therapist == null ? null : authenticator.therapistId(therapist);
            if (therapistId == null) {
                ApiJson.send(exchange, 401, "{\"error\":\"unauthorized\"}");
                return;
            }
            int months = parseMonths(exchange.getRequestURI().getRawQuery());
            String scope = parseScope(exchange.getRequestURI().getRawQuery());
            List<KpiMonthlySnapshot> snapshots = "global".equals(scope)
                    ? kpi.getRecentGlobalSnapshots(months)
                    : kpi.getRecentTherapistSnapshots(therapistId, months);
            StringBuilder json = new StringBuilder("{\"scope\":").append(ApiJson.quote(scope))
                    .append(",\"months\":").append(months).append(",\"series\":[");
            for (KpiMonthlySnapshot snapshot : snapshots) {
                if (json.charAt(json.length() - 1) != '[') json.append(',');
                json.append("{\"year\":").append(snapshot.getYear())
                        .append(",\"month\":").append(snapshot.getMonth())
                        .append(",\"appointmentsCreated\":").append(snapshot.getAppointmentsCreated())
                        .append(",\"appointmentsCompleted\":").append(snapshot.getAppointmentsCompleted())
                        .append(",\"appointmentsCancelled\":").append(snapshot.getAppointmentsCancelled())
                        .append(",\"activePatientsMonth\":").append(snapshot.getActivePatientsMonth())
                        .append(",\"newPatientsMonth\":").append(snapshot.getNewPatientsMonth())
                        .append(",\"treatmentPlansStarted\":").append(snapshot.getTreatmentPlansStarted())
                        .append(",\"treatmentSessionsCompleted\":").append(snapshot.getTreatmentSessionsCompleted())
                        .append(",\"totalBookedMinutes\":").append(snapshot.getTotalBookedMinutes())
                        .append(",\"appointmentsInMonth\":").append(snapshot.getAppointmentsInMonth())
                        .append(",\"newPatientsFirstAppointmentMonth\":").append(snapshot.getNewPatientsFirstAppointmentMonth())
                        .append(",\"returningPatientsMonth\":").append(snapshot.getReturningPatientsMonth())
                        .append(",\"agendaSaturationPct\":").append(snapshot.getAgendaSaturationPct())
                        .append(",\"appointmentsPerActivePatient\":").append(snapshot.getAppointmentsPerActivePatient())
                        .append(",\"computedAt\":").append(ApiJson.quote(snapshot.getComputedAt() == null
                                ? null : snapshot.getComputedAt().toString())).append('}');
            }
            ApiJson.send(exchange, 200, json.append("]}").toString());
        } catch (IllegalArgumentException exception) {
            ApiJson.send(exchange, 400, "{\"error\":\"invalid_months\"}");
        } catch (RuntimeException exception) {
            System.err.println("Errore lettura KPI: " + exception.getClass().getSimpleName());
            ApiJson.send(exchange, 503, "{\"error\":\"unavailable\"}");
        } finally {
            exchange.close();
        }
    }

    private int parseMonths(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) return 12;
        for (String field : rawQuery.split("&")) {
            String[] pair = field.split("=", 2);
            if ("months".equals(pair[0])) {
                int months = Integer.parseInt(URLDecoder.decode(pair.length > 1 ? pair[1] : "", StandardCharsets.UTF_8));
                if (months < 1 || months > 36) throw new IllegalArgumentException("months");
                return months;
            }
        }
        return 12;
    }

    private String parseScope(String rawQuery) {
        if (rawQuery == null) return "me";
        for (String field : rawQuery.split("&")) {
            String[] pair = field.split("=", 2);
            if ("scope".equals(pair[0])) {
                return "global".equalsIgnoreCase(URLDecoder.decode(pair.length > 1 ? pair[1] : "", StandardCharsets.UTF_8))
                        ? "global" : "me";
            }
        }
        return "me";
    }
}
