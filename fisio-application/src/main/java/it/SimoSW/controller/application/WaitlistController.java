package it.SimoSW.controller.application;

import it.SimoSW.model.UserRole;
import it.SimoSW.model.WaitlistEntry;
import it.SimoSW.model.dao.UserDAO;
import it.SimoSW.model.dao.WaitlistEntryDAO;

import java.util.List;
public class WaitlistController {

    private final WaitlistEntryDAO waitlistEntryDAO;
    private final UserDAO userDAO;

    public WaitlistController(WaitlistEntryDAO waitlistEntryDAO, UserDAO userDAO) {
        this.waitlistEntryDAO = waitlistEntryDAO;
        this.userDAO = userDAO;
    }

    public WaitlistEntry addEntry(WaitlistEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("Contatto lista di attesa non valido");
        }

        long therapistId = entry.getTherapistId();
        if (therapistId <= 0) {
            throw new IllegalArgumentException("Terapista non valido");
        }
        if (!userDAO.existsByIdAndRole(therapistId, UserRole.THERAPIST)) {
            throw new IllegalArgumentException("Terapista non valido");
        }

        entry.setFullName(normalizeRequired(entry.getFullName(), "Il nome e cognome sono obbligatori"));
        entry.setPhone(normalizePhone(entry.getPhone()));

        return waitlistEntryDAO.insert(entry);
    }

    public List<WaitlistEntry> getEntriesForTherapist(long therapistId) {
        if (therapistId <= 0) {
            throw new IllegalArgumentException("Terapista non valido");
        }
        if (!userDAO.existsByIdAndRole(therapistId, UserRole.THERAPIST)) {
            throw new IllegalArgumentException("Terapista non valido");
        }
        return waitlistEntryDAO.findAllByTherapist(therapistId);
    }

    public void removeEntry(long entryId, long therapistId) {
        if (entryId <= 0) {
            throw new IllegalArgumentException("Contatto lista di attesa non valido");
        }
        if (therapistId <= 0) {
            throw new IllegalArgumentException("Terapista non valido");
        }
        waitlistEntryDAO.deleteByIdAndTherapist(entryId, therapistId);
    }

    public long resolveTherapistIdByUsername(String username) {
        String normalized = normalizeOptional(username);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Sessione terapista non valida");
        }
        return userDAO.findIdByUsernameAndRole(username, UserRole.THERAPIST)
                .orElseThrow(() -> new IllegalArgumentException("Terapista non valido"));
    }

    private String normalizeRequired(String value, String errorMessage) {
        String normalized = normalizeOptional(value);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(errorMessage);
        }
        return normalized;
    }

    private String normalizePhone(String value) {
        return normalizeRequired(value, "Il numero di telefono è obbligatorio");
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }
}
