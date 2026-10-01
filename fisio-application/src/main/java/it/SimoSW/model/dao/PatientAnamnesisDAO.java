package it.SimoSW.model.dao;

import it.SimoSW.model.PatientAnamnesis;

import java.util.Optional;

public interface PatientAnamnesisDAO {

    PatientAnamnesis save(PatientAnamnesis anamnesis);

    Optional<PatientAnamnesis> findLatestByPatientId(long patientId);
}
