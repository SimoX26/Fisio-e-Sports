package it.SimoSW.model.dao;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface KpiMetricsDAO {
    List<Long> findActiveTherapistIds();
    int countAppointmentsCreated(LocalDateTime start, LocalDateTime end, Long therapistId);
    int countAppointmentsCompleted(LocalDateTime start, LocalDateTime end, Long therapistId);
    int countAppointmentsInMonth(LocalDateTime start, LocalDateTime end, Long therapistId);
    Map<String, Integer> queryAppointmentsInMonthTotals(LocalDateTime start, LocalDateTime end, Long therapistId);
    Map<String, Integer> queryNewPatientsByFirstAppointmentMonth(LocalDateTime start, LocalDateTime end, Long therapistId);
    int countAppointmentsCancelled(LocalDateTime start, LocalDateTime end, Long therapistId);
    int countActivePatientsMonth(LocalDateTime start, LocalDateTime end, Long therapistId);
    int countNewPatientsMonth(LocalDateTime start, LocalDateTime end, Long therapistId);
    int countTreatmentPlansStarted(LocalDate startDate, LocalDate endDate, Long therapistId);
    int countTreatmentSessionsCompleted(LocalDateTime start, LocalDateTime end, Long therapistId);
    int sumTotalBookedMinutes(LocalDateTime start, LocalDateTime end, Long therapistId);
}
