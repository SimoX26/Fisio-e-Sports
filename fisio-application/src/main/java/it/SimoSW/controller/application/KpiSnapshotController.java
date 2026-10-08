package it.SimoSW.controller.application;

import it.SimoSW.model.KpiMonthlySnapshot;
import it.SimoSW.model.dao.KpiMonthlySnapshotDAO;
import it.SimoSW.model.dao.KpiMetricsDAO;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

public class KpiSnapshotController {

    private static final String SOURCE_VERSION = "v1";
    // Placeholder temporaneo: capacità mensile per terapista (ore agenda disponibili).
    private static final int MONTHLY_AVAILABLE_MINUTES_PER_THERAPIST = 160 * 60;

    private final KpiMonthlySnapshotDAO kpiMonthlySnapshotDAO;
    private final KpiMetricsDAO metrics;

    public KpiSnapshotController(KpiMonthlySnapshotDAO kpiMonthlySnapshotDAO, KpiMetricsDAO metrics) {
        this.kpiMonthlySnapshotDAO = kpiMonthlySnapshotDAO;
        this.metrics = metrics;
    }

    public void refreshCurrentAndPreviousMonthSnapshots() {
        YearMonth current = YearMonth.now();
        YearMonth previous = current.minusMonths(1);

        refreshMonthSnapshots(current);
        refreshMonthSnapshots(previous);
    }

    public void refreshMonthSnapshots(YearMonth yearMonth) {
        if (yearMonth == null) {
            throw new IllegalArgumentException("yearMonth non valido");
        }

        saveSnapshot(buildGlobalSnapshot(yearMonth));
        for (Long therapistId : metrics.findActiveTherapistIds()) {
            saveSnapshot(buildTherapistSnapshot(yearMonth, therapistId));
        }
    }

    public List<KpiMonthlySnapshot> getRecentGlobalSnapshots(int months) {
        List<KpiMonthlySnapshot> snapshots = kpiMonthlySnapshotDAO.findRecentGlobal(months);
        enrichAppointmentsInMonth(snapshots, null);
        enrichManagementMetrics(snapshots, null);
        return snapshots;
    }

    public List<KpiMonthlySnapshot> getRecentTherapistSnapshots(long therapistId, int months) {
        List<KpiMonthlySnapshot> snapshots = kpiMonthlySnapshotDAO.findRecentByTherapist(therapistId, months);
        enrichAppointmentsInMonth(snapshots, therapistId);
        enrichManagementMetrics(snapshots, therapistId);
        return snapshots;
    }

    private void saveSnapshot(KpiMonthlySnapshot snapshot) {
        kpiMonthlySnapshotDAO.saveOrUpdate(snapshot);
    }

    private KpiMonthlySnapshot buildGlobalSnapshot(YearMonth yearMonth) {
        KpiMonthlySnapshot snapshot = baseSnapshot(yearMonth, "GLOBAL", 0L, null);
        LocalDateTime start = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime end = yearMonth.plusMonths(1).atDay(1).atStartOfDay();
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.plusMonths(1).atDay(1);

        snapshot.setAppointmentsCreated(metrics.countAppointmentsCreated(start, end, null));
        snapshot.setAppointmentsInMonth(metrics.countAppointmentsInMonth(start, end, null));
        snapshot.setAppointmentsCompleted(metrics.countAppointmentsCompleted(start, end, null));
        snapshot.setAppointmentsCancelled(metrics.countAppointmentsCancelled(start, end, null));
        snapshot.setActivePatientsMonth(metrics.countActivePatientsMonth(start, end, null));
        snapshot.setNewPatientsMonth(metrics.countNewPatientsMonth(start, end, null));
        snapshot.setTreatmentPlansStarted(metrics.countTreatmentPlansStarted(startDate, endDate, null));
        snapshot.setTreatmentSessionsCompleted(metrics.countTreatmentSessionsCompleted(start, end, null));
        snapshot.setTotalBookedMinutes(metrics.sumTotalBookedMinutes(start, end, null));
        return snapshot;
    }

    private KpiMonthlySnapshot buildTherapistSnapshot(YearMonth yearMonth, long therapistId) {
        KpiMonthlySnapshot snapshot = baseSnapshot(yearMonth, "THERAPIST", therapistId, therapistId);
        LocalDateTime start = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime end = yearMonth.plusMonths(1).atDay(1).atStartOfDay();
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.plusMonths(1).atDay(1);

        snapshot.setAppointmentsCreated(metrics.countAppointmentsCreated(start, end, therapistId));
        snapshot.setAppointmentsInMonth(metrics.countAppointmentsInMonth(start, end, therapistId));
        snapshot.setAppointmentsCompleted(metrics.countAppointmentsCompleted(start, end, therapistId));
        snapshot.setAppointmentsCancelled(metrics.countAppointmentsCancelled(start, end, therapistId));
        snapshot.setActivePatientsMonth(metrics.countActivePatientsMonth(start, end, therapistId));
        snapshot.setNewPatientsMonth(metrics.countNewPatientsMonth(start, end, therapistId));
        snapshot.setTreatmentPlansStarted(metrics.countTreatmentPlansStarted(startDate, endDate, therapistId));
        snapshot.setTreatmentSessionsCompleted(metrics.countTreatmentSessionsCompleted(start, end, therapistId));
        snapshot.setTotalBookedMinutes(metrics.sumTotalBookedMinutes(start, end, therapistId));
        return snapshot;
    }

    private KpiMonthlySnapshot baseSnapshot(YearMonth yearMonth, String scopeType, long scopeId, Long therapistId) {
        KpiMonthlySnapshot snapshot = new KpiMonthlySnapshot();
        snapshot.setScopeType(scopeType);
        snapshot.setScopeId(scopeId);
        snapshot.setTherapistId(therapistId);
        snapshot.setYear(yearMonth.getYear());
        snapshot.setMonth(yearMonth.getMonthValue());
        snapshot.setComputedAt(LocalDateTime.now());
        snapshot.setSourceVersion(SOURCE_VERSION);
        return snapshot;
    }

    private void enrichAppointmentsInMonth(List<KpiMonthlySnapshot> snapshots, Long therapistId) {
        if (snapshots == null || snapshots.isEmpty()) {
            return;
        }
        YearMonth min = null;
        YearMonth max = null;
        for (KpiMonthlySnapshot snapshot : snapshots) {
            YearMonth current = YearMonth.of(snapshot.getYear(), snapshot.getMonth());
            if (min == null || current.isBefore(min)) {
                min = current;
            }
            if (max == null || current.isAfter(max)) {
                max = current;
            }
        }
        if (min == null || max == null) {
            return;
        }

        LocalDateTime start = min.atDay(1).atStartOfDay();
        LocalDateTime end = max.plusMonths(1).atDay(1).atStartOfDay();
        Map<String, Integer> totalsByMonth = metrics.queryAppointmentsInMonthTotals(start, end, therapistId);

        for (KpiMonthlySnapshot snapshot : snapshots) {
            String key = snapshot.getYear() + "-" + snapshot.getMonth();
            snapshot.setAppointmentsInMonth(totalsByMonth.getOrDefault(key, 0));
        }
    }

    private void enrichManagementMetrics(List<KpiMonthlySnapshot> snapshots, Long therapistId) {
        if (snapshots == null || snapshots.isEmpty()) {
            return;
        }

        YearMonth min = null;
        YearMonth max = null;
        for (KpiMonthlySnapshot snapshot : snapshots) {
            YearMonth current = YearMonth.of(snapshot.getYear(), snapshot.getMonth());
            if (min == null || current.isBefore(min)) {
                min = current;
            }
            if (max == null || current.isAfter(max)) {
                max = current;
            }
        }
        if (min == null || max == null) {
            return;
        }

        LocalDateTime rangeStart = min.atDay(1).atStartOfDay();
        LocalDateTime rangeEnd = max.plusMonths(1).atDay(1).atStartOfDay();
        Map<String, Integer> newPatientsByFirstAppointmentMonth =
                metrics.queryNewPatientsByFirstAppointmentMonth(rangeStart, rangeEnd, therapistId);

        int therapistCapacityMultiplier = (therapistId == null) ? Math.max(metrics.findActiveTherapistIds().size(), 1) : 1;
        double availableMinutes = (double) MONTHLY_AVAILABLE_MINUTES_PER_THERAPIST * therapistCapacityMultiplier;

        for (KpiMonthlySnapshot snapshot : snapshots) {
            String key = snapshot.getYear() + "-" + snapshot.getMonth();
            int activePatients = Math.max(snapshot.getActivePatientsMonth(), 0);
            int newPatientsFirst = Math.max(newPatientsByFirstAppointmentMonth.getOrDefault(key, 0), 0);
            int returning = Math.max(activePatients - newPatientsFirst, 0);

            snapshot.setNewPatientsFirstAppointmentMonth(newPatientsFirst);
            snapshot.setReturningPatientsMonth(returning);

            double saturation = (availableMinutes <= 0D)
                    ? 0D
                    : ((double) Math.max(snapshot.getTotalBookedMinutes(), 0) / availableMinutes) * 100D;
            snapshot.setAgendaSaturationPct(roundOneDecimal(saturation));

            double appointmentsPerPatient = (activePatients == 0)
                    ? 0D
                    : ((double) Math.max(snapshot.getAppointmentsInMonth(), 0) / activePatients);
            snapshot.setAppointmentsPerActivePatient(roundOneDecimal(appointmentsPerPatient));
        }
    }

    private double roundOneDecimal(double value) {
        return Math.round(value * 10D) / 10D;
    }

}
