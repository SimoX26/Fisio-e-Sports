package it.SimoSW.model.dao.database;

import it.SimoSW.model.TreatmentPlan;
import it.SimoSW.model.TreatmentPlanState;
import it.SimoSW.model.dao.TreatmentPlanDAO;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DatabaseTreatmentPlanDAO implements TreatmentPlanDAO {

    private static final String INSERT = """
            INSERT INTO treatment_plans
            (patient_id, therapist_id, title, goals, frequency_per_week, start_date, expected_end_date, total_sessions_planned, state)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String UPDATE = """
            UPDATE treatment_plans
            SET patient_id = ?,
                therapist_id = ?,
                title = ?,
                goals = ?,
                frequency_per_week = ?,
                start_date = ?,
                expected_end_date = ?,
                total_sessions_planned = ?,
                state = ?
            WHERE id = ?
            """;

    private static final String FIND_BY_ID = """
            SELECT id, patient_id, therapist_id, title, goals, frequency_per_week, start_date, expected_end_date,
                   total_sessions_planned, state, created_at, updated_at
            FROM treatment_plans
            WHERE id = ?
            """;

    private static final String FIND_BY_THERAPIST = """
            SELECT id, patient_id, therapist_id, title, goals, frequency_per_week, start_date, expected_end_date,
                   total_sessions_planned, state, created_at, updated_at
            FROM treatment_plans
            WHERE therapist_id = ?
            ORDER BY created_at DESC
            """;

    private static final String FIND_BY_PATIENT = """
            SELECT id, patient_id, therapist_id, title, goals, frequency_per_week, start_date, expected_end_date,
                   total_sessions_planned, state, created_at, updated_at
            FROM treatment_plans
            WHERE patient_id = ?
            ORDER BY created_at DESC
            """;

    @Override
    public TreatmentPlan save(TreatmentPlan plan) {
        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement stmt = conn.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {

            bindWithoutId(stmt, plan);
            stmt.executeUpdate();

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    plan.setId(keys.getLong(1));
                }
            }

            return findById(plan.getId()).orElse(plan);
        } catch (SQLException e) {
            throw new RuntimeException("Errore salvataggio piano terapeutico", e);
        }
    }

    @Override
    public TreatmentPlan update(TreatmentPlan plan) {
        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement stmt = conn.prepareStatement(UPDATE)) {

            bindWithoutId(stmt, plan);
            stmt.setLong(10, plan.getId());

            int updatedRows = stmt.executeUpdate();
            if (updatedRows == 0) {
                throw new RuntimeException("Piano terapeutico non trovato: " + plan.getId());
            }
            return findById(plan.getId()).orElse(plan);
        } catch (SQLException e) {
            throw new RuntimeException("Errore update piano terapeutico", e);
        }
    }

    @Override
    public Optional<TreatmentPlan> findById(long id) {
        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement stmt = conn.prepareStatement(FIND_BY_ID)) {

            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Errore caricamento piano terapeutico", e);
        }
    }

    @Override
    public List<TreatmentPlan> findByTherapistId(long therapistId) {
        return queryMany(FIND_BY_THERAPIST, therapistId);
    }

    @Override
    public List<TreatmentPlan> findByPatientId(long patientId) {
        return queryMany(FIND_BY_PATIENT, patientId);
    }

    private List<TreatmentPlan> queryMany(String sql, long idValue) {
        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, idValue);
            List<TreatmentPlan> result = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("Errore query piani terapeutici", e);
        }
    }

    private void bindWithoutId(PreparedStatement stmt, TreatmentPlan plan) throws SQLException {
        if (plan.getPatientId() == null) {
            stmt.setNull(1, java.sql.Types.BIGINT);
        } else {
            stmt.setLong(1, plan.getPatientId());
        }
        stmt.setLong(2, plan.getTherapistId());
        stmt.setString(3, plan.getTitle());
        stmt.setString(4, plan.getGoals());

        if (plan.getFrequencyPerWeek() == null) {
            stmt.setNull(5, java.sql.Types.TINYINT);
        } else {
            stmt.setInt(5, plan.getFrequencyPerWeek());
        }

        stmt.setDate(6, Date.valueOf(plan.getStartDate()));
        if (plan.getExpectedEndDate() == null) {
            stmt.setNull(7, java.sql.Types.DATE);
        } else {
            stmt.setDate(7, Date.valueOf(plan.getExpectedEndDate()));
        }
        stmt.setInt(8, plan.getTotalSessionsPlanned());
        stmt.setString(9, plan.getState().name());
    }

    private TreatmentPlan mapRow(ResultSet rs) throws SQLException {
        TreatmentPlan plan = new TreatmentPlan();
        plan.setId(rs.getLong("id"));
        long patientId = rs.getLong("patient_id");
        plan.setPatientId(rs.wasNull() ? null : patientId);
        plan.setTherapistId(rs.getLong("therapist_id"));
        plan.setTitle(rs.getString("title"));
        plan.setGoals(rs.getString("goals"));

        int frequency = rs.getInt("frequency_per_week");
        plan.setFrequencyPerWeek(rs.wasNull() ? null : frequency);

        Date startDate = rs.getDate("start_date");
        if (startDate != null) {
            plan.setStartDate(startDate.toLocalDate());
        }

        Date expectedEndDate = rs.getDate("expected_end_date");
        if (expectedEndDate != null) {
            plan.setExpectedEndDate(expectedEndDate.toLocalDate());
        }

        plan.setTotalSessionsPlanned(rs.getInt("total_sessions_planned"));
        plan.setState(TreatmentPlanState.valueOf(rs.getString("state")));

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            plan.setCreatedAt(createdAt.toLocalDateTime());
        }

        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) {
            plan.setUpdatedAt(updatedAt.toLocalDateTime());
        }

        return plan;
    }
}
