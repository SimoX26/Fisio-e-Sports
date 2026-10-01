package it.SimoSW.model.dao;

import it.SimoSW.model.Patient;

import java.util.List;
import java.util.Optional;

public interface PatientDAO {

    Patient save(Patient patient);

    Patient update(Patient patient);

    Optional<Patient> findByIdForTherapist(long id, long therapistId);

    List<Patient> searchForTherapist(String query, long therapistId);

    void mergeInto(long sourcePatientId, long targetPatientId, long therapistId);

    void deleteByIdDetachingHistory(long id, String fallbackTitle, long therapistId);
}
