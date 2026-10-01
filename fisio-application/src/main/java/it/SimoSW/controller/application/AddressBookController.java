package it.SimoSW.controller.application;

import it.SimoSW.exception.InvalidPatientStateException;
import it.SimoSW.exception.PatientNotFoundException;
import it.SimoSW.model.PatientAnamnesis;
import it.SimoSW.model.PatientCondition;
import it.SimoSW.model.Patient;
import it.SimoSW.model.PatientState;
import it.SimoSW.model.UserRole;
import it.SimoSW.model.dao.PatientAnamnesisDAO;
import it.SimoSW.model.dao.PatientConditionDAO;
import it.SimoSW.model.dao.PatientDAO;
import it.SimoSW.model.dao.AppointmentDAO;
import it.SimoSW.model.dao.UserDAO;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Application controller responsible for managing patients in the address book.
 *
 * <p>This controller encapsulates the application-level business logic related to
 * patient lifecycle management (registration, update, activation, deactivation,
 * and archiving), acting as a boundary between the presentation layer and
 * the persistence layer.</p>
 *
 * <p>It enforces domain rules such as valid state transitions and prevents
 * illegal operations on archived patients.</p>
 */
public class AddressBookController {


    /**
     * Data Access Object used to persist and retrieve Patient entities.
     */
    private final PatientDAO patientDAO;
    private final PatientAnamnesisDAO patientAnamnesisDAO;
    private final PatientConditionDAO patientConditionDAO;
    private final AppointmentDAO appointmentDAO;
    private final UserDAO userDAO;


    /**
     * Constructs an AddressBookController with the given PatientDAO.
     *
     * @param patientDAO the DAO responsible for patient persistence
     */
    public AddressBookController(
            PatientDAO patientDAO,
            PatientAnamnesisDAO patientAnamnesisDAO,
            PatientConditionDAO patientConditionDAO,
            AppointmentDAO appointmentDAO,
            UserDAO userDAO
    ) {
        this.patientDAO = patientDAO;
        this.patientAnamnesisDAO = patientAnamnesisDAO;
        this.patientConditionDAO = patientConditionDAO;
        this.appointmentDAO = appointmentDAO;
        this.userDAO = userDAO;
    }


    /**
     * Registers a new patient in the system.
     *
     * <p>The patient is always initialized with the {@code ACTIVE} domain state,
     * regardless of any external input, ensuring a consistent starting state.</p>
     *
     * @param patient the patient to be registered
     * @return the persisted patient instance
     * @throws IllegalArgumentException if the patient is null
     */
    public Patient registerPatient(Patient patient, long therapistId) {
        if (patient == null) {
            throw new IllegalArgumentException("Patient cannot be null");
        }

        // Stato iniziale di dominio
        patient.setState(PatientState.ACTIVE);
        patient.setTherapistId(therapistId);

        // Persistenza tecnica
        return patientDAO.save(patient);
    }


    /**
     * Updates an existing patient's profile.
     *
     * <p>This method does not allow modifications to archived patients and
     * preserves the current patient state, unless explicitly changed by
     * a dedicated state-transition method.</p>
     *
     * @param patient the patient containing updated data
     * @return the updated patient
     * @throws IllegalArgumentException if the patient or its ID is null
     * @throws PatientNotFoundException if the patient does not exist
     * @throws InvalidPatientStateException if the patient is archived
     */
    public Patient updatePatientProfile(Patient patient, long therapistId) {
        if (patient == null || patient.getId() == 0) {
            throw new IllegalArgumentException("Patient or patient id cannot be null");
        }

        Patient existing = patientDAO.findByIdForTherapist(patient.getId(), therapistId)
                .orElseThrow(() -> new PatientNotFoundException(patient.getId()));

        if (existing.getState() == PatientState.ARCHIVED) {
            throw new InvalidPatientStateException("Archived patient cannot be modified");
        }

        // Manteniamo lo stato corrente se non deve cambiare qui
        patient.setState(existing.getState());
        patient.setTherapistId(therapistId);

        return patientDAO.update(patient);
    }


    /**
     * Retrieves a patient by its unique identifier.
     *
     * @param patientId the patient ID
     * @return the corresponding patient
     * @throws PatientNotFoundException if no patient is found
     */
    public Patient getPatientById(long patientId, long therapistId) {
        return patientDAO.findByIdForTherapist(patientId, therapistId)
                .orElseThrow(() -> new PatientNotFoundException(patientId));
    }


    /**
     * Searches patients using a free-text query.
     *
     * <p>The search logic is delegated to the DAO layer.</p>
     *
     * @param query the search query
     * @return a list of matching patients
     */
    public List<Patient> searchPatients(String query, long therapistId) {
        return patientDAO.searchForTherapist(query, therapistId);
    }

    public List<Patient> getPatientsTreatedInMonth(long therapistId, YearMonth yearMonth) {
        if (therapistId <= 0 || yearMonth == null) {
            throw new IllegalArgumentException("Parametri filtro mese non validi");
        }
        java.time.LocalDateTime start = yearMonth.atDay(1).atStartOfDay();
        java.time.LocalDateTime end = yearMonth.plusMonths(1).atDay(1).atStartOfDay();
        return getPatientsTreatedInPeriod(therapistId, start, end);
    }

    public List<Patient> getPatientsTreatedOnDate(long therapistId, LocalDate date) {
        if (therapistId <= 0 || date == null) {
            throw new IllegalArgumentException("Parametri filtro giorno non validi");
        }
        java.time.LocalDateTime start = date.atStartOfDay();
        java.time.LocalDateTime end = date.plusDays(1).atStartOfDay();
        return getPatientsTreatedInPeriod(therapistId, start, end);
    }

    private List<Patient> getPatientsTreatedInPeriod(long therapistId, java.time.LocalDateTime start, java.time.LocalDateTime end) {
        List<Long> patientIds = appointmentDAO.findDistinctPatientIdsByTherapistInPeriod(therapistId, start, end);
        List<Patient> patients = new java.util.ArrayList<>();
        for (Long patientId : patientIds) {
            if (patientId == null) {
                continue;
            }
            patientDAO.findByIdForTherapist(patientId, therapistId).ifPresent(patients::add);
        }
        return patients;
    }

    public List<Patient> findMergeCandidates(long sourcePatientId, String firstName, String lastName, long therapistId) {
        getPatientById(sourcePatientId, therapistId);
        String sourceName = normalizeFullName(firstName, lastName);
        if (sourceName.isEmpty()) {
            return List.of();
        }
        List<Patient> matches = patientDAO.searchForTherapist(sourceName, therapistId);
        List<Patient> candidates = new java.util.ArrayList<>();
        for (Patient patient : matches) {
            if (patient == null || patient.getId() == sourcePatientId) {
                continue;
            }
            if (normalizeSingle(patient.getFullName()).equals(sourceName)) {
                candidates.add(patient);
            }
        }
        return candidates;
    }


    /**
     * Activates a patient, if allowed by the domain rules.
     *
     * @param patientId the patient ID
     * @throws InvalidPatientStateException if the patient is archived
     */
    public void activatePatient(long patientId, long therapistId) {
        Patient patient = getPatientById(patientId, therapistId);

        if (patient.getState() == PatientState.ARCHIVED) {
            throw new InvalidPatientStateException("Archived patient cannot be activated");
        }

        patient.setState(PatientState.ACTIVE);
        patientDAO.update(patient);
    }


    /**
     * Deactivates a patient, if allowed by the domain rules.
     *
     * @param patientId the patient ID
     * @throws InvalidPatientStateException if the patient is archived
     */
    public void deactivatePatient(long patientId, long therapistId) {
        Patient patient = getPatientById(patientId, therapistId);

        if (patient.getState() == PatientState.ARCHIVED) {
            throw new InvalidPatientStateException("Archived patient cannot be deactivated");
        }

        patient.setState(PatientState.INACTIVE);
        patientDAO.update(patient);
    }


    /**
     * Archives a patient.
     *
     * <p>Once archived, a patient becomes immutable and cannot transition
     * to any other state.</p>
     *
     * @param patientId the patient ID
     * @throws InvalidPatientStateException if the patient is already archived
     */
    public void archivePatient(long patientId, long therapistId) {
        Patient patient = getPatientById(patientId, therapistId);

        if (patient.getState() == PatientState.ARCHIVED) {
            throw new InvalidPatientStateException("Patient already archived");
        }

        patient.setState(PatientState.ARCHIVED);
        patientDAO.update(patient);
    }

    public void deletePatient(long patientId, boolean forceDeleteWithLinkedAppointments, long therapistId) {
        Patient patient = getPatientById(patientId, therapistId);
        int linkedAppointments = appointmentDAO.countByPatientId(patientId);
        if (linkedAppointments > 0 && !forceDeleteWithLinkedAppointments) {
            throw new IllegalArgumentException(
                    "ATTENZIONE: il paziente ha " + linkedAppointments +
                            " appuntamenti collegati. Conferma l'eliminazione forzata per procedere."
            );
        }
        patientDAO.deleteByIdDetachingHistory(patientId, patient.getFullName(), therapistId);
    }

    public void mergePatients(long sourcePatientId, long targetPatientId, long therapistId) {
        if (sourcePatientId <= 0 || targetPatientId <= 0 || sourcePatientId == targetPatientId) {
            throw new IllegalArgumentException("Merge non valido: seleziona due contatti diversi");
        }
        getPatientById(sourcePatientId, therapistId);
        getPatientById(targetPatientId, therapistId);
        patientDAO.mergeInto(sourcePatientId, targetPatientId, therapistId);
    }

    public void savePatientAnamnesis(
            long patientId,
            String therapistUsername,
            PatientAnamnesis anamnesis,
            List<PatientCondition> conditions
    ) {
        long therapistId = resolveTherapistIdByUsername(therapistUsername);
        Patient existing = getPatientById(patientId, therapistId);

        if (existing.getState() == PatientState.ARCHIVED) {
            throw new InvalidPatientStateException("Archived patient cannot be modified");
        }

        anamnesis.setPatientId(patientId);
        anamnesis.setTherapistId(therapistId);

        PatientAnamnesis saved = patientAnamnesisDAO.save(anamnesis);

        if (conditions == null || conditions.isEmpty()) {
            return;
        }

        for (PatientCondition condition : conditions) {
            condition.setAnamnesisId(saved.getId());
        }
        patientConditionDAO.saveAll(saved.getId(), conditions);
    }

    public Optional<PatientAnamnesis> getLatestAnamnesisByPatientId(long patientId, long therapistId) {
        getPatientById(patientId, therapistId);
        return patientAnamnesisDAO.findLatestByPatientId(patientId);
    }

    public List<PatientCondition> getConditionsByAnamnesisId(long patientId, long anamnesisId, long therapistId) {
        PatientAnamnesis latest = getLatestAnamnesisByPatientId(patientId, therapistId)
                .orElseThrow(() -> new PatientNotFoundException(patientId));
        if (latest.getId() != anamnesisId) {
            throw new IllegalArgumentException("Anamnesi non autorizzata");
        }
        return patientConditionDAO.findByAnamnesisId(anamnesisId);
    }

    public long resolveTherapistIdByUsername(String therapistUsername) {
        String normalized = normalizeSingle(therapistUsername);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Sessione terapista non valida");
        }
        return userDAO.findIdByUsernameAndRole(therapistUsername, UserRole.THERAPIST)
                .orElseThrow(() -> new IllegalArgumentException("Terapista non valido"));
    }

    private String normalizeFullName(String firstName, String lastName) {
        String composed = (firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName);
        return normalizeSingle(composed);
    }

    private String normalizeSingle(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

}
