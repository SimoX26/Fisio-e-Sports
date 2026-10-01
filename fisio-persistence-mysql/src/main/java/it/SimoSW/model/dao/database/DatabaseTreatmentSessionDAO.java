package it.SimoSW.model.dao.database;

import it.SimoSW.model.TreatmentSession;
import it.SimoSW.model.TreatmentSessionState;
import it.SimoSW.model.dao.TreatmentSessionDAO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DatabaseTreatmentSessionDAO implements TreatmentSessionDAO {

    private static final String INSERT_SESSION = """
            INSERT INTO treatment_sessions
            (treatment_plan_id, appointment_id, patient_id, therapist_id, start_time, end_time,
             pain_score_pre, pain_score_post, session_outcome, home_exercises, notes, state)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String UPDATE_SESSION = """
            UPDATE treatment_sessions
            SET treatment_plan_id = ?,
                appointment_id = ?,
                patient_id = ?,
                therapist_id = ?,
                start_time = ?,
                end_time = ?,
                pain_score_pre = ?,
                pain_score_post = ?,
                session_outcome = ?,
                home_exercises = ?,
                notes = ?,
                state = ?
            WHERE id = ?
            """;

    private static final String FIND_BY_ID = """
            SELECT id, treatment_plan_id, appointment_id, patient_id, therapist_id, start_time, end_time,
                   pain_score_pre, pain_score_post, session_outcome, home_exercises, notes, state
            FROM treatment_sessions
            WHERE id = ?
            """;

    private static final String FIND_BY_APPOINTMENT_ID = """
            SELECT id, treatment_plan_id, appointment_id, patient_id, therapist_id, start_time, end_time,
                   pain_score_pre, pain_score_post, session_outcome, home_exercises, notes, state
            FROM treatment_sessions
            WHERE appointment_id = ?
            ORDER BY start_time DESC
            LIMIT 1
            """;

    private static final String FIND_BY_PATIENT_ID = """
            SELECT id, treatment_plan_id, appointment_id, patient_id, therapist_id, start_time, end_time,
                   pain_score_pre, pain_score_post, session_outcome, home_exercises, notes, state
            FROM treatment_sessions
            WHERE patient_id = ?
            ORDER BY start_time DESC
            """;

    private static final String FIND_BY_PATIENT_AND_THERAPIST_ID = """
            SELECT id, treatment_plan_id, appointment_id, patient_id, therapist_id, start_time, end_time,
                   pain_score_pre, pain_score_post, session_outcome, home_exercises, notes, state
            FROM treatment_sessions
            WHERE patient_id = ?
              AND therapist_id = ?
            ORDER BY start_time DESC
            """;

    private static final String FIND_BY_PLAN_ID = """
            SELECT id, treatment_plan_id, appointment_id, patient_id, therapist_id, start_time, end_time,
                   pain_score_pre, pain_score_post, session_outcome, home_exercises, notes, state
            FROM treatment_sessions
            WHERE treatment_plan_id = ?
            ORDER BY start_time ASC
            """;

    private static final String FIND_STARTED_HISTORY_FOR_THERAPIST_MULTI_PLAN = """
            SELECT ts.id, ts.treatment_plan_id, ts.appointment_id, ts.patient_id, ts.therapist_id, ts.start_time, ts.end_time,
                   ts.pain_score_pre, ts.pain_score_post, ts.session_outcome, ts.home_exercises, ts.notes, ts.state
            FROM treatment_sessions ts
            INNER JOIN treatment_plans tp ON tp.id = ts.treatment_plan_id
            WHERE ts.therapist_id = ?
              AND ts.state IN ('IN_PROGRESS', 'COMPLETED')
            ORDER BY ts.start_time DESC
            """;

    @Override
    public TreatmentSession save(TreatmentSession session) {
        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement stmt = conn.prepareStatement(INSERT_SESSION, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setLong(1, session.getTreatmentPlanId());
            if (session.getAppointmentId() == null) {
                stmt.setNull(2, java.sql.Types.BIGINT);
            } else {
                stmt.setLong(2, session.getAppointmentId());
            }
            if (session.getPatientId() == null) {
                stmt.setNull(3, java.sql.Types.BIGINT);
            } else {
                stmt.setLong(3, session.getPatientId());
            }
            stmt.setLong(4, session.getTherapistId());
            stmt.setTimestamp(5, Timestamp.valueOf(session.getStart()));
            stmt.setTimestamp(6, Timestamp.valueOf(session.getEnd()));
            if (session.getPainScorePre() == null) {
                stmt.setNull(7, java.sql.Types.TINYINT);
            } else {
                stmt.setInt(7, session.getPainScorePre());
            }
            if (session.getPainScorePost() == null) {
                stmt.setNull(8, java.sql.Types.TINYINT);
            } else {
                stmt.setInt(8, session.getPainScorePost());
            }
            stmt.setString(9, session.getSessionOutcome());
            stmt.setString(10, session.getHomeExercises());
            stmt.setString(11, session.getNotes());
            stmt.setString(12, session.getState().name());

            stmt.executeUpdate();

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    session.setId(keys.getLong(1));
                }
            }

            return session;
        } catch (SQLException e) {
            throw new RuntimeException("Errore durante il salvataggio della sessione", e);
        }
    }

    @Override
    public TreatmentSession update(TreatmentSession session) {
        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement stmt = conn.prepareStatement(UPDATE_SESSION)) {

            stmt.setLong(1, session.getTreatmentPlanId());
            if (session.getAppointmentId() == null) {
                stmt.setNull(2, java.sql.Types.BIGINT);
            } else {
                stmt.setLong(2, session.getAppointmentId());
            }
            if (session.getPatientId() == null) {
                stmt.setNull(3, java.sql.Types.BIGINT);
            } else {
                stmt.setLong(3, session.getPatientId());
            }
            stmt.setLong(4, session.getTherapistId());
            stmt.setTimestamp(5, Timestamp.valueOf(session.getStart()));
            stmt.setTimestamp(6, Timestamp.valueOf(session.getEnd()));
            if (session.getPainScorePre() == null) {
                stmt.setNull(7, java.sql.Types.TINYINT);
            } else {
                stmt.setInt(7, session.getPainScorePre());
            }
            if (session.getPainScorePost() == null) {
                stmt.setNull(8, java.sql.Types.TINYINT);
            } else {
                stmt.setInt(8, session.getPainScorePost());
            }
            stmt.setString(9, session.getSessionOutcome());
            stmt.setString(10, session.getHomeExercises());
            stmt.setString(11, session.getNotes());
            stmt.setString(12, session.getState().name());
            stmt.setLong(13, session.getId());

            int updatedRows = stmt.executeUpdate();
            if (updatedRows == 0) {
                throw new RuntimeException("Nessuna sessione aggiornata, id non trovato: " + session.getId());
            }

            return session;
        } catch (SQLException e) {
            throw new RuntimeException("Errore durante l'aggiornamento della sessione", e);
        }
    }

    @Override
    public Optional<TreatmentSession> findById(long id) {
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
            throw new RuntimeException("Errore durante il recupero della sessione", e);
        }
    }

    @Override
    public Optional<TreatmentSession> findByAppointmentId(long appointmentId) {
        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement stmt = conn.prepareStatement(FIND_BY_APPOINTMENT_ID)) {

            stmt.setLong(1, appointmentId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Errore durante il recupero della sessione per appointment", e);
        }
    }

    @Override
    public List<TreatmentSession> findByPatientId(long patientId) {
        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement stmt = conn.prepareStatement(FIND_BY_PATIENT_ID)) {

            stmt.setLong(1, patientId);

            List<TreatmentSession> result = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("Errore durante il recupero delle sessioni paziente", e);
        }
    }

    @Override
    public List<TreatmentSession> findByPatientIdAndTherapistId(long patientId, long therapistId) {
        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement stmt = conn.prepareStatement(FIND_BY_PATIENT_AND_THERAPIST_ID)) {

            stmt.setLong(1, patientId);
            stmt.setLong(2, therapistId);

            List<TreatmentSession> result = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("Errore durante il recupero delle sessioni paziente/terapista", e);
        }
    }

    @Override
    public List<TreatmentSession> findByTreatmentPlanId(long treatmentPlanId) {
        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement stmt = conn.prepareStatement(FIND_BY_PLAN_ID)) {

            stmt.setLong(1, treatmentPlanId);
            List<TreatmentSession> result = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("Errore durante il recupero delle sessioni per piano", e);
        }
    }

    @Override
    public List<TreatmentSession> findStartedHistoryForTherapistWithMultiSessionPlans(long therapistId) {
        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement stmt = conn.prepareStatement(FIND_STARTED_HISTORY_FOR_THERAPIST_MULTI_PLAN)) {

            stmt.setLong(1, therapistId);
            List<TreatmentSession> result = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("Errore durante il recupero storico trattamenti multi-sessione", e);
        }
    }

    private TreatmentSession mapRow(ResultSet rs) throws SQLException {
        TreatmentSession session = new TreatmentSession();
        session.setId(rs.getLong("id"));
        session.setTreatmentPlanId(rs.getLong("treatment_plan_id"));

        long appointmentId = rs.getLong("appointment_id");
        session.setAppointmentId(rs.wasNull() ? null : appointmentId);

        long patientId = rs.getLong("patient_id");
        session.setPatientId(rs.wasNull() ? null : patientId);
        session.setTherapistId(rs.getLong("therapist_id"));
        session.setStart(rs.getTimestamp("start_time").toLocalDateTime());
        session.setEnd(rs.getTimestamp("end_time").toLocalDateTime());

        int painScorePre = rs.getInt("pain_score_pre");
        session.setPainScorePre(rs.wasNull() ? null : painScorePre);

        int painScorePost = rs.getInt("pain_score_post");
        session.setPainScorePost(rs.wasNull() ? null : painScorePost);

        session.setSessionOutcome(rs.getString("session_outcome"));
        session.setHomeExercises(rs.getString("home_exercises"));
        session.setNotes(rs.getString("notes"));
        session.setState(TreatmentSessionState.valueOf(rs.getString("state")));
        return session;
    }
}
