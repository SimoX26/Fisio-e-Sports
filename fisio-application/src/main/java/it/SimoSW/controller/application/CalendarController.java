package it.SimoSW.controller.application;

import it.SimoSW.exception.AppointmentNotFoundException;
import it.SimoSW.exception.InvalidAppointmentStateException;
import it.SimoSW.exception.TimeSlotNotAvailableException;
import it.SimoSW.model.Appointment;
import it.SimoSW.model.AppointmentState;
import it.SimoSW.model.CalendarEventView;
import it.SimoSW.model.Patient;
import it.SimoSW.model.PatientState;
import it.SimoSW.model.UserRole;
import it.SimoSW.model.dao.AppointmentDAO;
import it.SimoSW.model.dao.PatientDAO;
import it.SimoSW.model.dao.UserDAO;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CalendarController {
    private static final int APPOINTMENT_BOUNDARY_MINUTES = 15;
    private static final int TRASH_RETENTION_DAYS = 30;
    private static final DateTimeFormatter TRASH_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final AppointmentDAO appointmentDAO;
    private final PatientDAO patientDAO;
    private final UserDAO userDAO;

    public CalendarController(AppointmentDAO appointmentDAO, PatientDAO patientDAO, UserDAO userDAO) {
        this.appointmentDAO = appointmentDAO;
        this.patientDAO = patientDAO;
        this.userDAO = userDAO;
    }

    public List<Appointment> getAppointmentsInPeriod(LocalDateTime start, LocalDateTime end) {
        validatePeriodRange(start, end);
        return appointmentDAO.findInPeriod(start, end);
    }

    public List<Appointment> getAppointmentsForTherapistInPeriod(long therapistId, LocalDateTime start, LocalDateTime end) {
        validatePeriodRange(start, end);
        checkTherapistUserExists(therapistId);
        return appointmentDAO.findByTherapistInPeriod(therapistId, start, end);
    }

    public List<Appointment> getReminderCandidatesForTherapistInPeriod(long therapistId, LocalDateTime start, LocalDateTime end) {
        return getAppointmentsForTherapistInPeriod(therapistId, start, end).stream()
                .filter(appointment -> appointment.getState() == AppointmentState.SCHEDULED)
                .filter(appointment -> appointment.getPatientId() != null)
                .toList();
    }

    public Appointment getAppointmentForTherapist(long appointmentId, long therapistId) {
        checkTherapistUserExists(therapistId);
        Appointment appointment = appointmentDAO.findById(appointmentId)
                .orElseThrow(() -> new AppointmentNotFoundException(appointmentId));
        if (appointment.getTherapistId() != therapistId) {
            throw new IllegalArgumentException("Appuntamento non autorizzato per questo terapista");
        }
        return appointment;
    }

    public List<CalendarEventView> getCalendarEventViewsForTherapistInPeriod(long therapistId, LocalDateTime start, LocalDateTime end) {
        validatePeriodRange(start, end);
        checkTherapistUserExists(therapistId);
        return appointmentDAO.findEventViewsByTherapistInPeriod(therapistId, start, end);
    }

    public List<CalendarEventView> searchEventViewsForTherapist(long therapistId, String query) {
        checkTherapistUserExists(therapistId);
        if (query == null || query.isBlank() || query.length() > 200) {
            throw new IllegalArgumentException("Query di ricerca non valida");
        }
        return appointmentDAO.searchEventViewsForTherapist(therapistId, query.trim());
    }

    public Appointment scheduleAppointment(Appointment appointment) {
        if (appointment == null) {
            throw new IllegalArgumentException("Appointment cannot be null");
        }

        validateTimeRange(appointment.getStart(), appointment.getEnd(), appointment.isAllDay());
        checkPatientExists(appointment.getPatientId(), appointment.getTherapistId());
        checkTherapistUserExists(appointment.getTherapistId());
        checkForConflicts(appointment);

        appointment.setState(AppointmentState.SCHEDULED);
        return appointmentDAO.save(appointment);
    }

    public Appointment rescheduleAppointment(long appointmentId, long therapistId, String patientName, LocalDateTime newStart, LocalDateTime newEnd, boolean allDay, boolean nonTreatmentEvent, String notes) {
        Appointment existing = getAppointmentForTherapist(appointmentId, therapistId);

        if (existing.getState() != AppointmentState.SCHEDULED) {
            throw new InvalidAppointmentStateException("Only scheduled appointments can be rescheduled");
        }

        validateTimeRange(newStart, newEnd, allDay);

        if (nonTreatmentEvent) {
            existing.setPatientId(null);
            existing.setTitle(patientName == null ? null : patientName.trim());
        } else if (patientName != null && !patientName.isBlank()) {
            long patientId = allDay
                    ? resolveExistingPatientId(patientName, existing.getTherapistId())
                    : resolveOrCreatePatientId(patientName, null, existing.getTherapistId());
            checkPatientExists(patientId, existing.getTherapistId());
            existing.setPatientId(patientId);
            existing.setTitle(null);
        }
        existing.setStart(newStart);
        existing.setEnd(newEnd);
        existing.setAllDay(allDay);
        existing.setNotes(normalizeNotes(notes));

        checkForConflicts(existing);

        return appointmentDAO.update(existing);
    }

    public void cancelAppointment(long appointmentId, long therapistId) {
        Appointment appointment = getAppointmentForTherapist(appointmentId, therapistId);
        boolean isNonTreatmentEvent = appointment.getPatientId() == null;

        if (appointment.getState() == AppointmentState.CANCELLED) {
            throw new InvalidAppointmentStateException("Appointment already cancelled");
        }

        if (appointment.getState() == AppointmentState.COMPLETED && !isNonTreatmentEvent) {
            throw new InvalidAppointmentStateException("Completed appointment cannot be cancelled");
        }

        appointment.setState(AppointmentState.CANCELLED);
        appointment.setCancelledAt(LocalDateTime.now());
        appointmentDAO.update(appointment);
    }

    public Appointment completeAppointment(long appointmentId, long therapistId) {
        Appointment appointment = getAppointmentForTherapist(appointmentId, therapistId);

        if (appointment.getState() == AppointmentState.CANCELLED) {
            throw new InvalidAppointmentStateException("Cancelled appointment cannot be completed");
        }

        if (appointment.getState() == AppointmentState.COMPLETED) {
            return appointment;
        }

        if (appointment.getEnd().isAfter(LocalDateTime.now())) {
            throw new InvalidAppointmentStateException("L'appuntamento non può essere completato prima dell'orario di fine");
        }

        appointment.setState(AppointmentState.COMPLETED);
        return appointmentDAO.update(appointment);
    }

    public List<CancelledAppointmentView> getCancelledAppointmentsForTherapist(long therapistId) {
        checkTherapistUserExists(therapistId);

        List<Appointment> cancelled = appointmentDAO.findCancelledByTherapist(therapistId);
        List<CancelledAppointmentView> result = new ArrayList<>(cancelled.size());

        for (Appointment appointment : cancelled) {
            result.add(new CancelledAppointmentView(
                    appointment.getId(),
                    resolvePatientFullName(appointment.getPatientId(), therapistId),
                    appointment.getStart(),
                    appointment.getEnd(),
                    appointment.getNotes()
            ));
        }

        return result;
    }

    public void restoreAppointment(long appointmentId, long therapistId) {
        checkTherapistUserExists(therapistId);

        Appointment appointment = appointmentDAO.findById(appointmentId)
                .orElseThrow(() -> new AppointmentNotFoundException(appointmentId));

        if (appointment.getTherapistId() != therapistId) {
            throw new IllegalArgumentException("Appuntamento non autorizzato per questo terapista");
        }

        if (appointment.getState() != AppointmentState.CANCELLED) {
            throw new InvalidAppointmentStateException("Solo gli appuntamenti cancellati possono essere ripristinati");
        }

        appointment.setState(AppointmentState.SCHEDULED);
        appointment.setCancelledAt(null);
        checkForConflicts(appointment);
        appointmentDAO.update(appointment);
    }

    public void deleteCancelledAppointment(long appointmentId, long therapistId) {
        checkTherapistUserExists(therapistId);

        Appointment appointment = appointmentDAO.findById(appointmentId)
                .orElseThrow(() -> new AppointmentNotFoundException(appointmentId));

        if (appointment.getTherapistId() != therapistId) {
            throw new IllegalArgumentException("Appuntamento non autorizzato per questo terapista");
        }

        if (appointment.getState() != AppointmentState.CANCELLED) {
            throw new InvalidAppointmentStateException("Solo gli appuntamenti cancellati possono essere eliminati definitivamente");
        }

        appointmentDAO.deleteById(appointmentId);
    }

    public int emptyTrashForTherapist(long therapistId) {
        checkTherapistUserExists(therapistId);
        return appointmentDAO.deleteCancelledByTherapist(therapistId);
    }

    public int purgeExpiredTrashForTherapist(long therapistId) {
        checkTherapistUserExists(therapistId);
        return appointmentDAO.deleteCancelledOlderThanDays(therapistId, TRASH_RETENTION_DAYS);
    }

    public long resolveOrCreatePatientId(String patientName, String patientPhone, long therapistId) {
        String normalizedInput = normalize(patientName);
        if (normalizedInput.isBlank()) {
            throw new IllegalArgumentException("Il nome del paziente è obbligatorio");
        }

        List<Patient> matches = patientDAO.searchForTherapist(patientName, therapistId);
        for (Patient existing : matches) {
            if (normalize(existing.getFullName()).equals(normalizedInput)) {
                return existing.getId();
            }
        }

        Patient newPatient = new Patient();
        String[] parts = patientName.trim().split("\\s+", 2);
        newPatient.setFirstName(parts[0]);
        newPatient.setLastName(parts.length > 1 ? parts[1] : "");
        newPatient.setEmail(null);
        newPatient.setPhone(normalizeOptionalPhone(patientPhone));
        newPatient.setState(PatientState.ACTIVE);
        newPatient.setTherapistId(therapistId);

        return patientDAO.save(newPatient).getId();
    }

    public long resolveExistingPatientId(String patientName, long therapistId) {
        String normalizedInput = normalize(patientName);
        if (normalizedInput.isBlank()) {
            throw new IllegalArgumentException("Per un evento tutto il giorno inserisci un paziente esistente");
        }

        List<Patient> matches = patientDAO.searchForTherapist(patientName, therapistId);
        for (Patient existing : matches) {
            if (normalize(existing.getFullName()).equals(normalizedInput)) {
                return existing.getId();
            }
        }

        throw new IllegalArgumentException("Paziente non trovato: per eventi tutto il giorno non viene creato automaticamente");
    }

    public long resolveTherapistUserIdFromUsername(String username) {
        String normalizedUsername = normalize(username);
        if (normalizedUsername.isBlank()) {
            throw new IllegalArgumentException("Utente loggato non valido");
        }

        return userDAO.findIdByUsernameAndRole(username, UserRole.THERAPIST)
                .orElseThrow(() -> new IllegalArgumentException("L'utente loggato non e un terapista attivo"));
    }

    public String resolvePatientFullName(Long patientId, long therapistId) {
        if (patientId == null) {
            return "Evento";
        }
        return patientDAO.findByIdForTherapist(patientId, therapistId)
                .map(Patient::getFullName)
                .orElse("Paziente #" + patientId);
    }

    public String resolvePatientEmail(Long patientId, long therapistId) {
        if (patientId == null) {
            return null;
        }
        return patientDAO.findByIdForTherapist(patientId, therapistId)
                .map(Patient::getEmail)
                .orElse(null);
    }

    public String resolvePatientPhone(Long patientId, long therapistId) {
        if (patientId == null) {
            return null;
        }
        return patientDAO.findByIdForTherapist(patientId, therapistId)
                .map(Patient::getPhone)
                .orElse(null);
    }

    public List<String> searchPatientNames(String query, int limit, long therapistId) {
        int normalizedLimit = Math.max(1, Math.min(limit, 20));
        List<Patient> matches = patientDAO.searchForTherapist(query, therapistId);
        List<String> result = new ArrayList<>();

        for (Patient patient : matches) {
            if (patient == null) {
                continue;
            }
            String fullName = patient.getFullName();
            if (fullName == null) {
                continue;
            }
            String normalizedName = fullName.trim();
            if (normalizedName.isEmpty() || result.contains(normalizedName)) {
                continue;
            }
            result.add(normalizedName);
            if (result.size() >= normalizedLimit) {
                break;
            }
        }
        return result;
    }

    private void validateTimeRange(LocalDateTime start, LocalDateTime end, boolean allDay) {
        if (start == null || end == null || !start.isBefore(end)) {
            throw new IllegalArgumentException("Invalid time range");
        }

        if (allDay) {
            if (!start.toLocalTime().equals(LocalTime.MIDNIGHT) || !end.toLocalTime().equals(LocalTime.MIDNIGHT)) {
                throw new IllegalArgumentException("Gli eventi tutto il giorno devono iniziare e finire a mezzanotte");
            }
            long durationDays = ChronoUnit.DAYS.between(start, end);
            if (durationDays < 1) {
                throw new IllegalArgumentException("La durata minima per un evento tutto il giorno e di 1 giorno");
            }
            return;
        }

        if (!isOnQuarterHourBoundary(start) || !isOnQuarterHourBoundary(end)) {
            throw new IllegalArgumentException("Gli appuntamenti devono iniziare e finire su intervalli di 15 minuti");
        }

        long durationMinutes = ChronoUnit.MINUTES.between(start, end);
        if (durationMinutes < APPOINTMENT_BOUNDARY_MINUTES) {
            throw new IllegalArgumentException("La durata minima dell'appuntamento e di 15 minuti");
        }
    }

    private void validatePeriodRange(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || !start.isBefore(end)) {
            throw new IllegalArgumentException("Invalid period range");
        }
    }

    private boolean isOnQuarterHourBoundary(LocalDateTime value) {
        return value.getMinute() % APPOINTMENT_BOUNDARY_MINUTES == 0
                && value.getSecond() == 0
                && value.getNano() == 0;
    }

    private void checkPatientExists(Long patientId, long therapistId) {
        if (patientId == null) {
            return;
        }
        patientDAO.findByIdForTherapist(patientId, therapistId)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found: " + patientId));
    }

    private String normalizeOptionalPhone(String patientPhone) {
        if (patientPhone == null) {
            return null;
        }
        String normalized = patientPhone.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private void checkTherapistUserExists(long therapistUserId) {
        if (therapistUserId <= 0) {
            throw new IllegalArgumentException("Invalid therapist user id");
        }
        if (!userDAO.existsByIdAndRole(therapistUserId, UserRole.THERAPIST)) {
            throw new IllegalArgumentException("Therapist user not found: " + therapistUserId);
        }
    }

    private void checkForConflicts(Appointment appointment) {
        List<Appointment> overlapping = appointmentDAO.findByTherapistInPeriod(
                appointment.getTherapistId(),
                appointment.getStart(),
                appointment.getEnd()
        );

        boolean hasConflict = overlapping.stream()
                .anyMatch(existing -> existing.getId() != appointment.getId());

        if (hasConflict) {
            throw new TimeSlotNotAvailableException();
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }

    private String normalizeNotes(String notes) {
        if (notes == null) {
            return null;
        }
        String normalized = notes.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public static class CancelledAppointmentView {
        private final long id;
        private final String patientFullName;
        private final LocalDateTime start;
        private final LocalDateTime end;
        private final String notes;

        public CancelledAppointmentView(long id, String patientFullName, LocalDateTime start, LocalDateTime end, String notes) {
            this.id = id;
            this.patientFullName = patientFullName;
            this.start = start;
            this.end = end;
            this.notes = notes;
        }

        public long getId() {
            return id;
        }

        public String getPatientFullName() {
            return patientFullName;
        }

        public LocalDateTime getStart() {
            return start;
        }

        public String getStartLabel() {
            return start == null ? "-" : start.format(TRASH_DATE_TIME_FORMATTER);
        }

        public LocalDateTime getEnd() {
            return end;
        }

        public String getEndLabel() {
            return end == null ? "-" : end.format(TRASH_DATE_TIME_FORMATTER);
        }

        public String getNotes() {
            return notes;
        }
    }
}
