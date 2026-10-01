package it.SimoSW.model.dao;

import it.SimoSW.model.Appointment;
import it.SimoSW.model.CalendarEventView;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AppointmentDAO {

    Appointment save(Appointment appointment);

    Appointment update(Appointment appointment);

    Optional<Appointment> findById(long id);

    List<Appointment> findInPeriod(LocalDateTime start, LocalDateTime end);

    List<Appointment> findByTherapistInPeriod(long therapistId, LocalDateTime start, LocalDateTime end);

    List<CalendarEventView> findEventViewsByTherapistInPeriod(long therapistId, LocalDateTime start, LocalDateTime end);

    List<Appointment> findCancelledByTherapist(long therapistId);

    List<Long> findDistinctPatientIdsByTherapistInPeriod(long therapistId, LocalDateTime start, LocalDateTime end);

    int countByPatientId(long patientId);

    int detachPatientFromAppointments(long patientId, String fallbackTitle);

    int deleteCancelledByTherapist(long therapistId);

    int deleteCancelledOlderThanDays(long therapistId, int retentionDays);

    void deleteById(long appointmentId);
}
