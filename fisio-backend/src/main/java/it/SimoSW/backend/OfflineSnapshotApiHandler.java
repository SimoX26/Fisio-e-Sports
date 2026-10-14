package it.SimoSW.backend;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import it.SimoSW.controller.application.AddressBookController;
import it.SimoSW.controller.application.CalendarController;
import it.SimoSW.controller.application.TreatmentController;
import it.SimoSW.controller.application.WaitlistController;
import it.SimoSW.model.CalendarEventView;
import it.SimoSW.model.Patient;
import it.SimoSW.model.PatientAnamnesis;
import it.SimoSW.model.TreatmentPlan;
import it.SimoSW.model.TreatmentSession;
import it.SimoSW.model.User;

import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Initial read-only export for a single authenticated therapist. */
final class OfflineSnapshotApiHandler implements HttpHandler {
    private final TherapistAuthenticator authenticator;
    private final AddressBookController addressBook;
    private final CalendarController calendar;
    private final TreatmentController treatments;
    private final WaitlistController waitlist;

    OfflineSnapshotApiHandler(TherapistAuthenticator authenticator, AddressBookController addressBook,
                              CalendarController calendar, TreatmentController treatments,
                              WaitlistController waitlist) {
        this.authenticator = authenticator;
        this.addressBook = addressBook;
        this.calendar = calendar;
        this.treatments = treatments;
        this.waitlist = waitlist;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            ApiJson.allowLocalFileOrigin(exchange, "GET, OPTIONS", "Authorization");
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"/api/offline/snapshot".equals(exchange.getRequestURI().getPath())) {
                ApiJson.send(exchange, 404, "{\"error\":\"not_found\"}");
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
            ApiJson.send(exchange, 200, snapshot(therapistId));
        } catch (RuntimeException exception) {
            System.err.println("Errore esportazione offline: " + exception.getClass().getSimpleName());
            ApiJson.send(exchange, 503, "{\"error\":\"unavailable\"}");
        } finally {
            exchange.close();
        }
    }

    private String snapshot(long therapistId) {
        List<Patient> patients = addressBook.searchPatients("", therapistId);
        Set<Long> patientIds = new HashSet<>();
        for (Patient patient : patients) patientIds.add(patient.getId());
        List<CalendarEventView> appointments = calendar.getAllCalendarEventViewsForTherapist(therapistId)
                .stream().map(event -> event.getPatientId() == null || patientIds.contains(event.getPatientId())
                        ? event : new CalendarEventView(event.getAppointmentId(), null, therapistId,
                        event.getStart(), event.getEnd(), event.isAllDay(), event.getNotes(),
                        event.getState(), "Paziente non disponibile", null))
                .collect(Collectors.toList());
        Set<Long> appointmentIds = new HashSet<>();
        for (CalendarEventView appointment : appointments) appointmentIds.add(appointment.getAppointmentId());

        StringBuilder json = new StringBuilder("{\"version\":1,\"generatedAt\":")
                .append(ApiJson.quote(Instant.now().toString()))
                .append(",\"patients\":").append(PatientsApiHandler.toJson(patients))
                .append(",\"anamneses\":[");
        boolean first = true;
        for (Patient patient : patients) {
            PatientAnamnesis anamnesis = addressBook.getLatestAnamnesisByPatientId(patient.getId(), therapistId)
                    .orElse(null);
            if (anamnesis == null) continue;
            if (!first) json.append(',');
            first = false;
            json.append("{\"patientId\":").append(patient.getId()).append(",\"data\":")
                    .append(PatientsApiHandler.anamnesisJson(anamnesis,
                            addressBook.getConditionsByAnamnesisId(patient.getId(), anamnesis.getId(), therapistId)))
                    .append('}');
        }
        json.append("],\"appointments\":").append(CalendarApiHandler.toJson(appointments))
                .append(",\"treatmentPlans\":[");
        List<TreatmentPlan> plans = treatments.getPlansForTherapist(therapistId);
        first = true;
        for (TreatmentPlan plan : plans) {
            if (!first) json.append(',');
            first = false;
            json.append(planJson(plan, patientIds));
        }
        json.append("],\"treatmentSessions\":[");
        first = true;
        for (TreatmentPlan plan : plans) {
            for (TreatmentSession session : treatments.getSessionsForPlan(plan.getId())) {
                if (session.getTherapistId() != therapistId) {
                    continue;
                }
                if (!first) json.append(',');
                first = false;
                json.append(sessionJson(session, patientIds, appointmentIds));
            }
        }
        return json.append("],\"waitlist\":")
                .append(WaitlistApiHandler.toJson(waitlist.getEntriesForTherapist(therapistId)))
                .append('}').toString();
    }

    private static String planJson(TreatmentPlan plan, Set<Long> patientIds) {
        StringBuilder json = new StringBuilder("{");
        field(json, "id", plan.getId());
        field(json, "patientId", patientIds.contains(plan.getPatientId()) ? plan.getPatientId() : null);
        field(json, "title", plan.getTitle());
        field(json, "goals", plan.getGoals());
        field(json, "frequencyPerWeek", plan.getFrequencyPerWeek());
        field(json, "startDate", plan.getStartDate());
        field(json, "expectedEndDate", plan.getExpectedEndDate());
        field(json, "totalSessionsPlanned", plan.getTotalSessionsPlanned());
        field(json, "state", plan.getState());
        field(json, "createdAt", plan.getCreatedAt());
        field(json, "updatedAt", plan.getUpdatedAt());
        return json.append('}').toString();
    }

    private static String sessionJson(TreatmentSession session, Set<Long> patientIds, Set<Long> appointmentIds) {
        StringBuilder json = new StringBuilder("{");
        field(json, "id", session.getId());
        field(json, "treatmentPlanId", session.getTreatmentPlanId());
        field(json, "appointmentId", appointmentIds.contains(session.getAppointmentId()) ? session.getAppointmentId() : null);
        field(json, "patientId", patientIds.contains(session.getPatientId()) ? session.getPatientId() : null);
        field(json, "start", session.getStart());
        field(json, "end", session.getEnd());
        field(json, "painScorePre", session.getPainScorePre());
        field(json, "painScorePost", session.getPainScorePost());
        field(json, "outcome", session.getSessionOutcome());
        field(json, "homeExercises", session.getHomeExercises());
        field(json, "notes", session.getNotes());
        field(json, "state", session.getState());
        return json.append('}').toString();
    }

    private static void field(StringBuilder json, String name, Object value) {
        if (json.length() > 1) json.append(',');
        json.append(ApiJson.quote(name)).append(':');
        json.append(value == null ? "null" : value instanceof Number || value instanceof Boolean
                ? value : ApiJson.quote(value.toString()));
    }
}
