package it.SimoSW.model.dao.database;

import it.SimoSW.model.dao.KpiMetricsDAO;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DatabaseKpiMetricsDAO implements KpiMetricsDAO {

    @Override
    public List<Long> findActiveTherapistIds() {
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

    @Override
    public int countAppointmentsCreated(LocalDateTime start, LocalDateTime end, Long therapistId) {
        String sql = """
                SELECT COUNT(*) AS total
                FROM appointments
                WHERE created_at >= ? AND created_at < ?
                """ + therapistFilterSql("therapist_id", therapistId);
        return runCountByDateTime(sql, start, end, therapistId);
    }

    @Override
    public int countAppointmentsCompleted(LocalDateTime start, LocalDateTime end, Long therapistId) {
        String sql = """
                SELECT COUNT(*) AS total
                FROM appointments
                WHERE state = 'COMPLETED'
                  AND end_time >= ? AND end_time < ?
                """ + therapistFilterSql("therapist_id", therapistId);
        return runCountByDateTime(sql, start, end, therapistId);
    }

    @Override
    public int countAppointmentsInMonth(LocalDateTime start, LocalDateTime end, Long therapistId) {
        String sql = """
                SELECT COUNT(*) AS total
                FROM appointments
                WHERE state <> 'CANCELLED'
                  AND patient_id IS NOT NULL
                  AND start_time >= ? AND start_time < ?
                """ + therapistFilterSql("therapist_id", therapistId);
        return runCountByDateTime(sql, start, end, therapistId);
    }

    @Override
    public Map<String, Integer> queryAppointmentsInMonthTotals(LocalDateTime start, LocalDateTime end, Long therapistId) {
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

    @Override
    public Map<String, Integer> queryNewPatientsByFirstAppointmentMonth(LocalDateTime start, LocalDateTime end, Long therapistId) {
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

    @Override
    public int countAppointmentsCancelled(LocalDateTime start, LocalDateTime end, Long therapistId) {
        String sql = """
                SELECT COUNT(*) AS total
                FROM appointments
                WHERE state = 'CANCELLED'
                  AND cancelled_at >= ? AND cancelled_at < ?
                """ + therapistFilterSql("therapist_id", therapistId);
        return runCountByDateTime(sql, start, end, therapistId);
    }

    @Override
    public int countActivePatientsMonth(LocalDateTime start, LocalDateTime end, Long therapistId) {
        String sql = """
                SELECT COUNT(DISTINCT patient_id) AS total
                FROM appointments
                WHERE patient_id IS NOT NULL
                  AND state <> 'CANCELLED'
                  AND start_time >= ? AND start_time < ?
                """ + therapistFilterSql("therapist_id", therapistId);
        return runCountByDateTime(sql, start, end, therapistId);
    }

    @Override
    public int countNewPatientsMonth(LocalDateTime start, LocalDateTime end, Long therapistId) {
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

    @Override
    public int countTreatmentPlansStarted(LocalDate startDate, LocalDate endDate, Long therapistId) {
        String sql = """
                SELECT COUNT(*) AS total
                FROM treatment_plans
                WHERE start_date >= ? AND start_date < ?
                """ + therapistFilterSql("therapist_id", therapistId);
        return runCountByDate(sql, startDate, endDate, therapistId);
    }

    @Override
    public int countTreatmentSessionsCompleted(LocalDateTime start, LocalDateTime end, Long therapistId) {
        String sql = """
                SELECT COUNT(*) AS total
                FROM treatment_sessions
                WHERE state = 'COMPLETED'
                  AND end_time >= ? AND end_time < ?
                """ + therapistFilterSql("therapist_id", therapistId);
        return runCountByDateTime(sql, start, end, therapistId);
    }

    @Override
    public int sumTotalBookedMinutes(LocalDateTime start, LocalDateTime end, Long therapistId) {
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
