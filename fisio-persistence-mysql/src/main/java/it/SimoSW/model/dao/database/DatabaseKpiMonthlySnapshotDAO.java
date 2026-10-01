package it.SimoSW.model.dao.database;

import it.SimoSW.model.KpiMonthlySnapshot;
import it.SimoSW.model.dao.KpiMonthlySnapshotDAO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class DatabaseKpiMonthlySnapshotDAO implements KpiMonthlySnapshotDAO {

    private static final String UPSERT_SQL = """
            INSERT INTO kpi_monthly_snapshot (
                scope_type, scope_id, therapist_id, year, month,
                appointments_created, appointments_completed, appointments_cancelled,
                active_patients_month, new_patients_month, treatment_plans_started,
                treatment_sessions_completed, total_booked_minutes,
                computed_at, source_version
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                therapist_id = VALUES(therapist_id),
                appointments_created = VALUES(appointments_created),
                appointments_completed = VALUES(appointments_completed),
                appointments_cancelled = VALUES(appointments_cancelled),
                active_patients_month = VALUES(active_patients_month),
                new_patients_month = VALUES(new_patients_month),
                treatment_plans_started = VALUES(treatment_plans_started),
                treatment_sessions_completed = VALUES(treatment_sessions_completed),
                total_booked_minutes = VALUES(total_booked_minutes),
                computed_at = VALUES(computed_at),
                source_version = VALUES(source_version)
            """;

    private static final String FIND_RECENT_GLOBAL_SQL = """
            SELECT id, scope_type, scope_id, therapist_id, year, month,
                   appointments_created, appointments_completed, appointments_cancelled,
                   active_patients_month, new_patients_month, treatment_plans_started,
                   treatment_sessions_completed, total_booked_minutes,
                   computed_at, source_version, created_at, updated_at
            FROM kpi_monthly_snapshot
            WHERE scope_type = 'GLOBAL'
              AND scope_id = 0
            ORDER BY year DESC, month DESC
            LIMIT ?
            """;

    private static final String FIND_RECENT_BY_THERAPIST_SQL = """
            SELECT id, scope_type, scope_id, therapist_id, year, month,
                   appointments_created, appointments_completed, appointments_cancelled,
                   active_patients_month, new_patients_month, treatment_plans_started,
                   treatment_sessions_completed, total_booked_minutes,
                   computed_at, source_version, created_at, updated_at
            FROM kpi_monthly_snapshot
            WHERE scope_type = 'THERAPIST'
              AND therapist_id = ?
            ORDER BY year DESC, month DESC
            LIMIT ?
            """;

    @Override
    public void saveOrUpdate(KpiMonthlySnapshot snapshot) {
        validateSnapshot(snapshot);

        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement stmt = conn.prepareStatement(UPSERT_SQL)) {

            stmt.setString(1, snapshot.getScopeType());
            stmt.setLong(2, snapshot.getScopeId());
            if (snapshot.getTherapistId() == null) {
                stmt.setNull(3, Types.BIGINT);
            } else {
                stmt.setLong(3, snapshot.getTherapistId());
            }
            stmt.setInt(4, snapshot.getYear());
            stmt.setInt(5, snapshot.getMonth());
            stmt.setInt(6, snapshot.getAppointmentsCreated());
            stmt.setInt(7, snapshot.getAppointmentsCompleted());
            stmt.setInt(8, snapshot.getAppointmentsCancelled());
            stmt.setInt(9, snapshot.getActivePatientsMonth());
            stmt.setInt(10, snapshot.getNewPatientsMonth());
            stmt.setInt(11, snapshot.getTreatmentPlansStarted());
            stmt.setInt(12, snapshot.getTreatmentSessionsCompleted());
            stmt.setInt(13, snapshot.getTotalBookedMinutes());
            stmt.setTimestamp(14, Timestamp.valueOf(snapshot.getComputedAt()));
            stmt.setString(15, snapshot.getSourceVersion());

            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Errore salvataggio snapshot KPI mensile", e);
        }
    }

    @Override
    public List<KpiMonthlySnapshot> findRecentGlobal(int limitMonths) {
        int normalizedLimit = normalizeLimit(limitMonths);
        return queryRecent(FIND_RECENT_GLOBAL_SQL, normalizedLimit, null);
    }

    @Override
    public List<KpiMonthlySnapshot> findRecentByTherapist(long therapistId, int limitMonths) {
        if (therapistId <= 0) {
            throw new IllegalArgumentException("therapistId non valido");
        }
        int normalizedLimit = normalizeLimit(limitMonths);
        return queryRecent(FIND_RECENT_BY_THERAPIST_SQL, normalizedLimit, therapistId);
    }

    private List<KpiMonthlySnapshot> queryRecent(String sql, int limitMonths, Long therapistId) {
        List<KpiMonthlySnapshot> result = new ArrayList<>();
        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            if (therapistId == null) {
                stmt.setInt(1, limitMonths);
            } else {
                stmt.setLong(1, therapistId);
                stmt.setInt(2, limitMonths);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }

            return result;
        } catch (SQLException e) {
            throw new RuntimeException("Errore lettura snapshot KPI mensili", e);
        }
    }

    private KpiMonthlySnapshot mapRow(ResultSet rs) throws SQLException {
        KpiMonthlySnapshot snapshot = new KpiMonthlySnapshot();
        snapshot.setId(rs.getLong("id"));
        snapshot.setScopeType(rs.getString("scope_type"));
        snapshot.setScopeId(rs.getLong("scope_id"));
        long therapistIdRaw = rs.getLong("therapist_id");
        snapshot.setTherapistId(rs.wasNull() ? null : therapistIdRaw);
        snapshot.setYear(rs.getInt("year"));
        snapshot.setMonth(rs.getInt("month"));
        snapshot.setAppointmentsCreated(rs.getInt("appointments_created"));
        snapshot.setAppointmentsCompleted(rs.getInt("appointments_completed"));
        snapshot.setAppointmentsCancelled(rs.getInt("appointments_cancelled"));
        snapshot.setActivePatientsMonth(rs.getInt("active_patients_month"));
        snapshot.setNewPatientsMonth(rs.getInt("new_patients_month"));
        snapshot.setTreatmentPlansStarted(rs.getInt("treatment_plans_started"));
        snapshot.setTreatmentSessionsCompleted(rs.getInt("treatment_sessions_completed"));
        snapshot.setTotalBookedMinutes(rs.getInt("total_booked_minutes"));
        snapshot.setComputedAt(toLocalDateTime(rs.getTimestamp("computed_at")));
        snapshot.setSourceVersion(rs.getString("source_version"));
        snapshot.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        snapshot.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        return snapshot;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private int normalizeLimit(int limitMonths) {
        if (limitMonths <= 0) {
            return 12;
        }
        return Math.min(limitMonths, 36);
    }

    private void validateSnapshot(KpiMonthlySnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot non può essere null");
        }
        String scopeType = snapshot.getScopeType();
        if (!"GLOBAL".equals(scopeType) && !"THERAPIST".equals(scopeType)) {
            throw new IllegalArgumentException("scopeType non valido");
        }
        if (snapshot.getMonth() < 1 || snapshot.getMonth() > 12) {
            throw new IllegalArgumentException("month non valido");
        }
        if (snapshot.getYear() < 2000 || snapshot.getYear() > 3000) {
            throw new IllegalArgumentException("year non valido");
        }
        if (snapshot.getComputedAt() == null) {
            snapshot.setComputedAt(LocalDateTime.now());
        }
        if (snapshot.getSourceVersion() == null || snapshot.getSourceVersion().isBlank()) {
            snapshot.setSourceVersion("v1");
        }

        if ("GLOBAL".equals(scopeType)) {
            snapshot.setScopeId(0L);
            snapshot.setTherapistId(null);
        } else {
            if (snapshot.getTherapistId() == null || snapshot.getTherapistId() <= 0) {
                throw new IllegalArgumentException("therapistId non valido per scope THERAPIST");
            }
            snapshot.setScopeId(snapshot.getTherapistId());
        }
    }
}
