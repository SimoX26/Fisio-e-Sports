package it.SimoSW.model.dao;

import it.SimoSW.model.TreatmentPlan;

import java.util.List;
import java.util.Optional;

public interface TreatmentPlanDAO {
    TreatmentPlan save(TreatmentPlan plan);

    TreatmentPlan update(TreatmentPlan plan);

    Optional<TreatmentPlan> findById(long id);

    List<TreatmentPlan> findByTherapistId(long therapistId);

    List<TreatmentPlan> findByPatientId(long patientId);
}
