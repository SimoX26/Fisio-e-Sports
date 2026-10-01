package it.SimoSW.model.dao;

import it.SimoSW.model.TreatmentSession;

import java.util.List;
import java.util.Optional;

public interface TreatmentSessionDAO {
    TreatmentSession save(TreatmentSession session);

    TreatmentSession update(TreatmentSession session);

    Optional<TreatmentSession> findById(long id);

    Optional<TreatmentSession> findByAppointmentId(long appointmentId);

    List<TreatmentSession> findByPatientId(long patientId);

    List<TreatmentSession> findByPatientIdAndTherapistId(long patientId, long therapistId);

    List<TreatmentSession> findByTreatmentPlanId(long treatmentPlanId);

    List<TreatmentSession> findStartedHistoryForTherapistWithMultiSessionPlans(long therapistId);
}
