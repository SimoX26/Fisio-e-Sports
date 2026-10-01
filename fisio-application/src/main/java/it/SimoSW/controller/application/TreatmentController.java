package it.SimoSW.controller.application;

import it.SimoSW.model.Patient;
import it.SimoSW.model.Appointment;
import it.SimoSW.model.AppointmentState;
import it.SimoSW.model.TreatmentPlan;
import it.SimoSW.model.TreatmentPlanState;
import it.SimoSW.model.TreatmentSession;
import it.SimoSW.model.TreatmentSessionState;
import it.SimoSW.model.dao.AppointmentDAO;
import it.SimoSW.model.dao.PatientDAO;
import it.SimoSW.model.dao.TreatmentPlanDAO;
import it.SimoSW.model.dao.TreatmentSessionDAO;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TreatmentController {
    private static final DateTimeFormatter TITLE_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HISTORY_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final TreatmentPlanDAO treatmentPlanDAO;
    private final TreatmentSessionDAO treatmentSessionDAO;
    private final PatientDAO patientDAO;
    private final AppointmentDAO appointmentDAO;

    public TreatmentController(
            TreatmentPlanDAO treatmentPlanDAO,
            TreatmentSessionDAO treatmentSessionDAO,
            PatientDAO patientDAO,
            AppointmentDAO appointmentDAO
    ) {
        this.treatmentPlanDAO = treatmentPlanDAO;
        this.treatmentSessionDAO = treatmentSessionDAO;
        this.patientDAO = patientDAO;
        this.appointmentDAO = appointmentDAO;
    }

    public TreatmentPlan createTreatmentPlan(TreatmentPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("Piano terapeutico obbligatorio");
        }
        if (plan.getTitle() == null || plan.getTitle().isBlank()) {
            throw new IllegalArgumentException("Titolo piano terapeutico obbligatorio");
        }
        if (plan.getTotalSessionsPlanned() < 1) {
            throw new IllegalArgumentException("Il piano deve prevedere almeno una seduta");
        }
        if (plan.getStartDate() == null) {
            plan.setStartDate(LocalDate.now());
        }
        if (plan.getPatientId() == null || plan.getPatientId() <= 0) {
            throw new IllegalArgumentException("Paziente piano terapeutico non valido");
        }

        patientDAO.findByIdForTherapist(plan.getPatientId(), plan.getTherapistId())
                .orElseThrow(() -> new IllegalArgumentException("Paziente non trovato: " + plan.getPatientId()));

        plan.setState(TreatmentPlanState.ACTIVE);
        return treatmentPlanDAO.save(plan);
    }

    public TreatmentSession scheduleSession(TreatmentSession session) {
        if (session == null) {
            throw new IllegalArgumentException("Sessione obbligatoria");
        }
        if (session.getStart() == null || session.getEnd() == null || !session.getEnd().isAfter(session.getStart())) {
            throw new IllegalArgumentException("Intervallo orario sessione non valido");
        }

        TreatmentPlan plan = treatmentPlanDAO.findById(session.getTreatmentPlanId())
                .orElseThrow(() -> new IllegalArgumentException("Piano terapeutico non trovato: " + session.getTreatmentPlanId()));

        if (plan.getState() != TreatmentPlanState.ACTIVE) {
            throw new IllegalStateException("Impossibile pianificare sedute su piano non attivo");
        }

        if (session.getPatientId() == null || !plan.getPatientId().equals(session.getPatientId())) {
            throw new IllegalArgumentException("Paziente della sessione non coerente con il piano");
        }
        if (plan.getTherapistId() != session.getTherapistId()) {
            throw new IllegalArgumentException("Terapista della sessione non coerente con il piano");
        }

        if (session.getAppointmentId() != null) {
            appointmentDAO.findById(session.getAppointmentId())
                    .orElseThrow(() -> new IllegalArgumentException("Appuntamento non trovato: " + session.getAppointmentId()));
        }

        if (session.getState() == null) {
            session.setState(TreatmentSessionState.PLANNED);
        }

        validatePainScores(session.getPainScorePre(), session.getPainScorePost());
        return treatmentSessionDAO.save(session);
    }

    public TreatmentSession startSession(long sessionId) {
        TreatmentSession session = treatmentSessionDAO.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Sessione non trovata: " + sessionId));

        if (session.getState() != TreatmentSessionState.PLANNED) {
            throw new IllegalStateException("Solo una sessione pianificata puo essere avviata");
        }

        session.setState(TreatmentSessionState.IN_PROGRESS);
        return treatmentSessionDAO.update(session);
    }

    public TreatmentSession completeSession(long sessionId,
                                            Integer painScorePre,
                                            Integer painScorePost,
                                            String outcome,
                                            String homeExercises,
                                            String notes) {
        TreatmentSession session = treatmentSessionDAO.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Sessione non trovata: " + sessionId));

        if (session.getState() != TreatmentSessionState.IN_PROGRESS) {
            throw new IllegalStateException("Solo una sessione avviata puo essere completata");
        }

        validatePainScores(painScorePre, painScorePost);
        session.setPainScorePre(painScorePre);
        session.setPainScorePost(painScorePost);
        session.setSessionOutcome(outcome);
        session.setHomeExercises(homeExercises);
        session.setNotes(notes);
        session.setState(TreatmentSessionState.COMPLETED);
        return treatmentSessionDAO.update(session);
    }

    public TreatmentSession cancelSession(long sessionId, String cancellationNote) {
        TreatmentSession session = treatmentSessionDAO.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Sessione non trovata: " + sessionId));

        if (session.getState() == TreatmentSessionState.COMPLETED) {
            throw new IllegalStateException("Una sessione completata non può essere annullata");
        }

        session.setNotes(cancellationNote);
        session.setState(TreatmentSessionState.CANCELLED);
        return treatmentSessionDAO.update(session);
    }

    public List<TreatmentPlan> getPlansForTherapist(long therapistId) {
        return treatmentPlanDAO.findByTherapistId(therapistId);
    }

    public List<TreatmentSession> getSessionsForPlan(long treatmentPlanId) {
        return treatmentSessionDAO.findByTreatmentPlanId(treatmentPlanId);
    }

    public List<TreatmentHistoryEntry> getTreatmentChronologyForPatient(long therapistId, long patientId) {
        patientDAO.findByIdForTherapist(patientId, therapistId)
                .orElseThrow(() -> new IllegalArgumentException("Paziente non trovato"));
        List<TreatmentSession> sessions = treatmentSessionDAO
                .findByPatientIdAndTherapistId(patientId, therapistId);
        return buildHistoryEntries(sessions);
    }

    public TreatmentSession ensureTreatmentForCompletedAppointment(long appointmentId) {
        Appointment appointment = appointmentDAO.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("Appuntamento non trovato: " + appointmentId));

        if (appointment.getState() != AppointmentState.COMPLETED) {
            throw new IllegalStateException("Il trattamento automatico richiede un appuntamento completato");
        }
        if (appointment.isAllDay()) {
            throw new IllegalStateException("Gli eventi tutto il giorno non sono collegati ai trattamenti");
        }

        return treatmentSessionDAO.findByAppointmentId(appointmentId)
                .orElseGet(() -> createAutomaticTreatmentFromAppointment(appointment));
    }

    public TreatmentSession createTreatmentForCompletedAppointment(long appointmentId,
                                                                   String planTitle,
                                                                   String goals,
                                                                   Integer frequencyPerWeek,
                                                                   LocalDate expectedEndDate,
                                                                   Integer totalSessionsPlanned,
                                                                   Integer painScorePre,
                                                                   Integer painScorePost,
                                                                   String sessionOutcome,
                                                                   String homeExercises,
                                                                   String notes) {
        Appointment appointment = appointmentDAO.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("Appuntamento non trovato: " + appointmentId));

        if (appointment.getState() != AppointmentState.COMPLETED) {
            throw new IllegalStateException("Il trattamento richiede un appuntamento completato");
        }
        if (appointment.isAllDay()) {
            throw new IllegalStateException("Gli eventi tutto il giorno non sono collegati ai trattamenti");
        }
        if (treatmentSessionDAO.findByAppointmentId(appointmentId).isPresent()) {
            throw new IllegalStateException("Esiste già un trattamento associato a questo appuntamento");
        }

        String normalizedPlanTitle = normalizeRequired(planTitle, "Titolo piano terapeutico obbligatorio");
        Integer normalizedTotalSessions = totalSessionsPlanned == null ? 1 : totalSessionsPlanned;
        if (normalizedTotalSessions < 1) {
            throw new IllegalArgumentException("Il piano deve prevedere almeno una seduta");
        }
        if (frequencyPerWeek != null && frequencyPerWeek < 1) {
            throw new IllegalArgumentException("La frequenza settimanale deve essere almeno 1");
        }

        validatePainScores(painScorePre, painScorePost);

        TreatmentPlan plan = new TreatmentPlan();
        plan.setPatientId(appointment.getPatientId());
        plan.setTherapistId(appointment.getTherapistId());
        plan.setTitle(normalizedPlanTitle);
        plan.setGoals(normalizeOptional(goals));
        plan.setFrequencyPerWeek(frequencyPerWeek);
        plan.setStartDate(appointment.getStart().toLocalDate());
        plan.setExpectedEndDate(expectedEndDate);
        plan.setTotalSessionsPlanned(normalizedTotalSessions);
        plan.setState(normalizedTotalSessions > 1 ? TreatmentPlanState.ACTIVE : TreatmentPlanState.COMPLETED);
        TreatmentPlan savedPlan = treatmentPlanDAO.save(plan);

        TreatmentSession session = new TreatmentSession();
        session.setTreatmentPlanId(savedPlan.getId());
        session.setAppointmentId(appointment.getId());
        session.setPatientId(appointment.getPatientId());
        session.setTherapistId(appointment.getTherapistId());
        session.setStart(appointment.getStart());
        session.setEnd(appointment.getEnd());
        session.setPainScorePre(painScorePre);
        session.setPainScorePost(painScorePost);
        session.setSessionOutcome(normalizeOptional(sessionOutcome));
        session.setHomeExercises(normalizeOptional(homeExercises));
        session.setNotes(normalizeOptional(notes));
        session.setState(TreatmentSessionState.COMPLETED);
        return treatmentSessionDAO.save(session);
    }

    public List<TreatmentHistoryEntry> getStartedHistoryForTherapistWithMultiSessionPlans(long therapistId) {
        List<TreatmentSession> sessions = treatmentSessionDAO
                .findStartedHistoryForTherapistWithMultiSessionPlans(therapistId);
        return buildHistoryEntries(sessions);
    }

    private List<TreatmentHistoryEntry> buildHistoryEntries(List<TreatmentSession> sessions) {
        Map<Long, TreatmentPlan> planCache = new HashMap<>();
        Map<Long, Patient> patientCache = new HashMap<>();
        List<TreatmentHistoryEntry> history = new ArrayList<>();

        for (TreatmentSession session : sessions) {
            TreatmentPlan plan = planCache.computeIfAbsent(
                    session.getTreatmentPlanId(),
                    id -> treatmentPlanDAO.findById(id).orElse(null)
            );
            if (plan == null) {
                continue;
            }

            Long patientId = session.getPatientId();
            Patient patient = patientId == null ? null : patientCache.computeIfAbsent(
                    patientId,
                    id -> patientDAO.findByIdForTherapist(id, session.getTherapistId()).orElse(null)
            );

            TreatmentHistoryEntry entry = new TreatmentHistoryEntry();
            entry.setSessionId(session.getId());
            entry.setPlanId(plan.getId());
            entry.setPlanTitle(plan.getTitle());
            entry.setPatientId(patient == null ? 0 : patient.getId());
            entry.setPatientName(patient == null ? "Paziente eliminato" : patient.getFullName());
            entry.setSessionStart(session.getStart());
            entry.setSessionEnd(session.getEnd());
            entry.setState(session.getState());
            entry.setOutcome(session.getSessionOutcome());
            entry.setPainScorePre(session.getPainScorePre());
            entry.setPainScorePost(session.getPainScorePost());
            history.add(entry);
        }

        return history;
    }

    private TreatmentSession createAutomaticTreatmentFromAppointment(Appointment appointment) {
        return createTreatmentForCompletedAppointment(
                appointment.getId(),
                "Trattamento da appuntamento " + TITLE_DATE_FORMATTER.format(appointment.getStart().toLocalDate()),
                "Creato automaticamente dal completamento appuntamento",
                null,
                appointment.getEnd().toLocalDate(),
                1,
                null,
                null,
                "Sessione registrata automaticamente da appuntamento completato",
                null,
                appointment.getNotes()
        );
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeRequired(String value, String errorMessage) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new IllegalArgumentException(errorMessage);
        }
        return normalized;
    }

    private void validatePainScores(Integer pre, Integer post) {
        if (pre != null && (pre < 0 || pre > 10)) {
            throw new IllegalArgumentException("Pain score pre deve essere compreso tra 0 e 10");
        }
        if (post != null && (post < 0 || post > 10)) {
            throw new IllegalArgumentException("Pain score post deve essere compreso tra 0 e 10");
        }
    }

    public static class TreatmentHistoryEntry {
        private long sessionId;
        private long planId;
        private String planTitle;
        private long patientId;
        private String patientName;
        private LocalDateTime sessionStart;
        private LocalDateTime sessionEnd;
        private TreatmentSessionState state;
        private String outcome;
        private Integer painScorePre;
        private Integer painScorePost;

        public long getSessionId() {
            return sessionId;
        }

        public void setSessionId(long sessionId) {
            this.sessionId = sessionId;
        }

        public long getPlanId() {
            return planId;
        }

        public void setPlanId(long planId) {
            this.planId = planId;
        }

        public String getPlanTitle() {
            return planTitle;
        }

        public void setPlanTitle(String planTitle) {
            this.planTitle = planTitle;
        }

        public long getPatientId() {
            return patientId;
        }

        public void setPatientId(long patientId) {
            this.patientId = patientId;
        }

        public String getPatientName() {
            return patientName;
        }

        public void setPatientName(String patientName) {
            this.patientName = patientName;
        }

        public LocalDateTime getSessionStart() {
            return sessionStart;
        }

        public void setSessionStart(LocalDateTime sessionStart) {
            this.sessionStart = sessionStart;
        }

        public LocalDateTime getSessionEnd() {
            return sessionEnd;
        }

        public void setSessionEnd(LocalDateTime sessionEnd) {
            this.sessionEnd = sessionEnd;
        }

        public TreatmentSessionState getState() {
            return state;
        }

        public void setState(TreatmentSessionState state) {
            this.state = state;
        }

        public String getOutcome() {
            return outcome;
        }

        public void setOutcome(String outcome) {
            this.outcome = outcome;
        }

        public Integer getPainScorePre() {
            return painScorePre;
        }

        public void setPainScorePre(Integer painScorePre) {
            this.painScorePre = painScorePre;
        }

        public Integer getPainScorePost() {
            return painScorePost;
        }

        public void setPainScorePost(Integer painScorePost) {
            this.painScorePost = painScorePost;
        }

        public String getSessionDateLabel() {
            if (sessionStart == null) {
                return "-";
            }
            return HISTORY_DATE_FORMATTER.format(sessionStart.toLocalDate());
        }

        public String getStateLabel() {
            if (state == null) {
                return "-";
            }
            return switch (state) {
                case PLANNED -> "Pianificata";
                case IN_PROGRESS -> "In corso";
                case COMPLETED -> "Completata";
                case CANCELLED -> "Annullata";
            };
        }
    }
}
