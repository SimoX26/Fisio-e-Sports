package it.SimoSW.controller.application;

import it.SimoSW.model.KpiMonthlySnapshot;
import it.SimoSW.model.dao.KpiMonthlySnapshotDAO;
import it.SimoSW.model.dao.database.ConnectionFactory;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KpiSnapshotController {

    private static final String SOURCE_VERSION = "v1";
    // Placeholder temporaneo: capacità mensile per terapista (ore agenda disponibili).
    private static final int MONTHLY_AVAILABLE_MINUTES_PER_THERAPIST = 160 * 60;

    private final KpiMonthlySnapshotDAO kpiMonthlySnapshotDAO;

    public KpiSnapshotController(KpiMonthlySnapshotDAO kpiMonthlySnapshotDAO) {
        this.kpiMonthlySnapshotDAO = kpiMonthlySnapshotDAO;
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
        for (Long therapistId : findActiveTherapistIds()) {
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

        snapshot.setAppointmentsCreated(countAppointmentsCreated(start, end, null));
        snapshot.setAppointmentsInMonth(countAppointmentsInMonth(start, end, null));
        snapshot.setAppointmentsCompleted(countAppointmentsCompleted(start, end, null));
        snapshot.setAppointmentsCancelled(countAppointmentsCancelled(start, end, null));
        snapshot.setActivePatientsMonth(countActivePatientsMonth(start, end, null));
        snapshot.setNewPatientsMonth(countNewPatientsMonth(start, end, null));
        snapshot.setTreatmentPlansStarted(countTreatmentPlansStarted(startDate, endDate, null));
        snapshot.setTreatmentSessionsCompleted(countTreatmentSessionsCompleted(start, end, null));
        snapshot.setTotalBookedMinutes(sumTotalBookedMinutes(start, end, null));
        return snapshot;
    }

    private KpiMonthlySnapshot buildTherapistSnapshot(YearMonth yearMonth, long therapistId) {
        KpiMonthlySnapshot snapshot = baseSnapshot(yearMonth, "THERAPIST", therapistId, therapistId);
        LocalDateTime start = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime end = yearMonth.plusMonths(1).atDay(1).atStartOfDay();
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.plusMonths(1).atDay(1);

        snapshot.setAppointmentsCreated(countAppointmentsCreated(start, end, therapistId));
        snapshot.setAppointmentsInMonth(countAppointmentsInMonth(start, end, therapistId));
        snapshot.setAppointmentsCompleted(countAppointmentsCompleted(start, end, therapistId));
        snapshot.setAppointmentsCancelled(countAppointmentsCancelled(start, end, therapistId));
        snapshot.setActivePatientsMonth(countActivePatientsMonth(start, end, therapistId));
        snapshot.setNewPatientsMonth(countNewPatientsMonth(start, end, therapistId));
        snapshot.setTreatmentPlansStarted(countTreatmentPlansStarted(startDate, endDate, therapistId));
        snapshot.setTreatmentSessionsCompleted(countTreatmentSessionsCompleted(start, end, therapistId));
        snapshot.setTotalBookedMinutes(sumTotalBookedMinutes(start, end, therapistId));
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

    private List<Long> findActiveTherapistIds() {
        String sql = """
                SELECT id
                FROM users
                WHERE role = 'THERAPIST' AND active = TRUE
                ORDER BY id
                """;
        List<Long> ids = new ArrayList<>();
        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                ids.add(rs.getLong("id"));
            }
            return ids;
        } catch (SQLException e) {
            throw new RuntimeException("Errore lettura terapisti attivi per KPI snapshot", e);
        }
    }

    private int countAppointmentsCreated(LocalDateTime start, LocalDateTime end, Long therapistId) {
        String sql = """
                SELECT COUNT(*) AS total
                FROM appointments
                WHERE created_at >= ? AND created_at < ?
                """ + therapistFilterSql("therapist_id", therapistId);
        return runCountByDateTime(sql, start, end, therapistId);
    }

    private int countAppointmentsCompleted(LocalDateTime start, LocalDateTime end, Long therapistId) {
        String sql = """
                SELECT COUNT(*) AS total
                FROM appointments
                WHERE state = 'COMPLETED'
                  AND end_time >= ? AND end_time < ?
                """ + therapistFilterSql("therapist_id", therapistId);
        return runCountByDateTime(sql, start, end, therapistId);
    }

    private int countAppointmentsInMonth(LocalDateTime start, LocalDateTime end, Long therapistId) {
        String sql = """
                SELECT COUNT(*) AS total
                FROM appointments
                WHERE state <> 'CANCELLED'
                  AND patient_id IS NOT NULL
                  AND start_time >= ? AND start_time < ?
                """ + therapistFilterSql("therapist_id", therapistId);
        return runCountByDateTime(sql, start, end, therapistId);
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
        Map<String, Integer> totalsByMonth = queryAppointmentsInMonthTotals(start, end, therapistId);

        for (KpiMonthlySnapshot snapshot : snapshots) {
            String key = snapshot.getYear() + "-" + snapshot.getMonth();
            snapshot.setAppointmentsInMonth(totalsByMonth.getOrDefault(key, 0));
        }
    }

    private Map<String, Integer> queryAppointmentsInMonthTotals(LocalDateTime start, LocalDateTime end, Long therapistId) {
        String sql = """
                SELECT YEAR(start_time) AS y, MONTH(start_time) AS m, COUNT(*) AS total
                FROM appointments
                WHERE state <> 'CANCELLED'
                  AND patient_id IS NOT NULL
                  AND start_time >= ? AND start_time < ?
                """ + therapistFilterSql("therapist_id", therapistId) + """
                GROUP BY YEAR(start_time), MONTH(start_time)
                """;

        Map<String, Integer> result = new HashMap<>();
        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(start));
            stmt.setTimestamp(2, Timestamp.valueOf(end));
            if (therapistId != null) {
                stmt.setLong(3, therapistId);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getInt("y") + "-" + rs.getInt("m");
                    result.put(key, rs.getInt("total"));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("Errore calcolo appuntamenti mensili operativi", e);
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
                queryNewPatientsByFirstAppointmentMonth(rangeStart, rangeEnd, therapistId);

        int therapistCapacityMultiplier = (therapistId == null) ? Math.max(findActiveTherapistIds().size(), 1) : 1;
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

    private Map<String, Integer> queryNewPatientsByFirstAppointmentMonth(LocalDateTime start, LocalDateTime end, Long therapistId) {
        String sql = """
                SELECT YEAR(first_start) AS y, MONTH(first_start) AS m, COUNT(*) AS total
                FROM (
                    SELECT patient_id, MIN(start_time) AS first_start
                    FROM appointments
                    WHERE state <> 'CANCELLED'
                      AND patient_id IS NOT NULL
                """ + therapistFilterSql("therapist_id", therapistId) + """
                    GROUP BY patient_id
                ) first_appointments
                WHERE first_start >= ? AND first_start < ?
                GROUP BY YEAR(first_start), MONTH(first_start)
                """;

        Map<String, Integer> result = new HashMap<>();
        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            int index = 1;
            if (therapistId != null) {
                stmt.setLong(index++, therapistId);
            }
            stmt.setTimestamp(index++, Timestamp.valueOf(start));
            stmt.setTimestamp(index, Timestamp.valueOf(end));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getInt("y") + "-" + rs.getInt("m");
                    result.put(key, rs.getInt("total"));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("Errore calcolo nuovi pazienti da primo appuntamento", e);
        }
    }

    private double roundOneDecimal(double value) {
        return Math.round(value * 10D) / 10D;
    }

    private int countAppointmentsCancelled(LocalDateTime start, LocalDateTime end, Long therapistId) {
        String sql = """
                SELECT COUNT(*) AS total
                FROM appointments
                WHERE state = 'CANCELLED'
                  AND cancelled_at >= ? AND cancelled_at < ?
                """ + therapistFilterSql("therapist_id", therapistId);
        return runCountByDateTime(sql, start, end, therapistId);
    }

    private int countActivePatientsMonth(LocalDateTime start, LocalDateTime end, Long therapistId) {
        String sql = """
                SELECT COUNT(DISTINCT patient_id) AS total
                FROM appointments
                WHERE patient_id IS NOT NULL
                  AND state <> 'CANCELLED'
                  AND start_time >= ? AND start_time < ?
                """ + therapistFilterSql("therapist_id", therapistId);
        return runCountByDateTime(sql, start, end, therapistId);
    }

    private int countNewPatientsMonth(LocalDateTime start, LocalDateTime end, Long therapistId) {
        if (therapistId == null) {
            String sql = """
                    SELECT COUNT(*) AS total
                    FROM patients
                    WHERE created_at >= ? AND created_at < ?
                    """;
            return runCountByDateTime(sql, start, end, null);
        }

        String sql = """
                SELECT COUNT(DISTINCT p.id) AS total
                FROM patients p
                JOIN appointments a ON a.patient_id = p.id
                WHERE p.created_at >= ? AND p.created_at < ?
                  AND a.therapist_id = ?
                """;
        return runCountByDateTime(sql, start, end, therapistId);
    }

    private int countTreatmentPlansStarted(LocalDate startDate, LocalDate endDate, Long therapistId) {
        String sql = """
                SELECT COUNT(*) AS total
                FROM treatment_plans
                WHERE start_date >= ? AND start_date < ?
                """ + therapistFilterSql("therapist_id", therapistId);
        return runCountByDate(sql, startDate, endDate, therapistId);
    }

    private int countTreatmentSessionsCompleted(LocalDateTime start, LocalDateTime end, Long therapistId) {
        String sql = """
                SELECT COUNT(*) AS total
                FROM treatment_sessions
                WHERE state = 'COMPLETED'
                  AND end_time >= ? AND end_time < ?
                """ + therapistFilterSql("therapist_id", therapistId);
        return runCountByDateTime(sql, start, end, therapistId);
    }

    private int sumTotalBookedMinutes(LocalDateTime start, LocalDateTime end, Long therapistId) {
        String sql = """
                SELECT COALESCE(SUM(TIMESTAMPDIFF(MINUTE, start_time, end_time)), 0) AS total
                FROM appointments
                WHERE state <> 'CANCELLED'
                  AND patient_id IS NOT NULL
                  AND start_time >= ? AND start_time < ?
                """ + therapistFilterSql("therapist_id", therapistId);
        return runCountByDateTime(sql, start, end, therapistId);
    }

    private String therapistFilterSql(String column, Long therapistId) {
        if (therapistId == null) {
            return "";
        }
        return " AND " + column + " = ? ";
    }

    private int runCountByDateTime(String sql, LocalDateTime start, LocalDateTime end, Long therapistId) {
        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(start));
            stmt.setTimestamp(2, Timestamp.valueOf(end));
            if (therapistId != null) {
                stmt.setLong(3, therapistId);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return 0;
                }
                return rs.getInt("total");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Errore calcolo KPI snapshot mensili", e);
        }
    }

    private int runCountByDate(String sql, LocalDate start, LocalDate end, Long therapistId) {
        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDate(1, Date.valueOf(start));
            stmt.setDate(2, Date.valueOf(end));
            if (therapistId != null) {
                stmt.setLong(3, therapistId);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return 0;
                }
                return rs.getInt("total");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Errore calcolo KPI snapshot mensili", e);
        }
    }
}
