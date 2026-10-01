package it.SimoSW.model.dao.database;

import it.SimoSW.model.dao.AppointmentDAO;
import it.SimoSW.model.Appointment;
import it.SimoSW.model.AppointmentState;
import it.SimoSW.model.CalendarEventView;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DatabaseAppointmentDAO implements AppointmentDAO {

    @Override
    public Appointment save(Appointment appointment) {

        String sql = """
            INSERT INTO appointments
            (patient_id, therapist_id, start_time, end_time, all_day, title, notes, state)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            if (appointment.getPatientId() == null) {
                ps.setNull(1, Types.BIGINT);
            } else {
                ps.setLong(1, appointment.getPatientId());
            }
            ps.setLong(2, appointment.getTherapistId());
            ps.setTimestamp(3, Timestamp.valueOf(appointment.getStart()));
            ps.setTimestamp(4, Timestamp.valueOf(appointment.getEnd()));
            ps.setBoolean(5, appointment.isAllDay());
            ps.setString(6, appointment.getTitle());
            ps.setString(7, appointment.getNotes());
            ps.setString(8, appointment.getState().name());

            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    appointment.setId(keys.getLong(1));
                }
            }

            return appointment;

        } catch (SQLException e) {
            throw new RuntimeException("Errore durante il salvataggio dell'appuntamento", e);
        }
    }


    @Override
    public Appointment update(Appointment appointment) {

        String sql = """
            UPDATE appointments
            SET patient_id = ?,
                therapist_id = ?,
                start_time = ?,
                end_time = ?,
                all_day = ?,
                title = ?,
                notes = ?,
                state = ?,
                cancelled_at = ?
            WHERE id = ?
        """;

        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            if (appointment.getPatientId() == null) {
                ps.setNull(1, Types.BIGINT);
            } else {
                ps.setLong(1, appointment.getPatientId());
            }
            ps.setLong(2, appointment.getTherapistId());
            ps.setTimestamp(3, Timestamp.valueOf(appointment.getStart()));
            ps.setTimestamp(4, Timestamp.valueOf(appointment.getEnd()));
            ps.setBoolean(5, appointment.isAllDay());
            ps.setString(6, appointment.getTitle());
            ps.setString(7, appointment.getNotes());
            ps.setString(8, appointment.getState().name());
            if (appointment.getCancelledAt() == null) {
                ps.setNull(9, Types.TIMESTAMP);
            } else {
                ps.setTimestamp(9, Timestamp.valueOf(appointment.getCancelledAt()));
            }
            ps.setLong(10, appointment.getId());

            int updatedRows = ps.executeUpdate();

            if (updatedRows == 0) {
                throw new RuntimeException(
                        "Nessun appuntamento aggiornato, id non trovato: " + appointment.getId()
                );
            }

            return appointment;

        } catch (SQLException e) {
            throw new RuntimeException("Errore durante l'update dell'appuntamento", e);
        }
    }


    @Override
    public Optional<Appointment> findById(long id) {

        String sql = "SELECT * FROM appointments WHERE id = ?";

        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }

        } catch (SQLException e) {
            throw new RuntimeException("Errore durante il recupero dell'appuntamento", e);
        }
    }


    @Override
    public List<Appointment> findInPeriod(LocalDateTime start, LocalDateTime end) {

        String sql = """
            SELECT * FROM appointments
            WHERE start_time >= ? AND end_time <= ?
              AND state <> 'CANCELLED'
            ORDER BY start_time
        """;

        List<Appointment> result = new ArrayList<>();

        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setTimestamp(1, Timestamp.valueOf(start));
            ps.setTimestamp(2, Timestamp.valueOf(end));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }

            return result;

        } catch (SQLException e) {
            throw new RuntimeException("Errore caricamento appuntamenti nel periodo", e);
        }
    }


    @Override
    public List<Appointment> findByTherapistInPeriod(long therapistId, LocalDateTime start, LocalDateTime end) {
        String sql = """
            SELECT * FROM appointments
            WHERE therapist_id = ?
              AND state <> 'CANCELLED'
              AND start_time < ?
              AND end_time > ?
            ORDER BY start_time
        """;

        List<Appointment> result = new ArrayList<>();

        try (Connection conn = ConnectionFactory.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, therapistId);
            ps.setTimestamp(2, Timestamp.valueOf(end));
            ps.setTimestamp(3, Timestamp.valueOf(start));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }

            return result;

        } catch (SQLException e) {
            throw new RuntimeException("Errore caricamento appuntamenti terapista", e);
        }
    }

    @Override
    public List<CalendarEventView> findEventViewsByTherapistInPeriod(long therapistId, LocalDateTime start, LocalDateTime end) {
        String sql = """
            SELECT a.id,
                   a.patient_id,
                   a.therapist_id,
                   a.start_time,
                   a.end_time,
                   a.all_day,
                   a.title,
                   a.notes,
                   a.state,
                   p.first_name,
                   p.last_name,
                   p.phone
            FROM appointments a
            LEFT JOIN patients p ON p.id = a.patient_id AND p.therapist_id = a.therapist_id
            WHERE a.therapist_id = ?
              AND a.state <> 'CANCELLED'
              AND a.start_time < ?
              AND a.end_time > ?
            ORDER BY a.start_time
        """;

        List<CalendarEventView> result = new ArrayList<>();

        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, therapistId);
            ps.setTimestamp(2, Timestamp.valueOf(end));
            ps.setTimestamp(3, Timestamp.valueOf(start));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String firstName = repairMojibake(rs.getString("first_name"));
                    String lastName = repairMojibake(rs.getString("last_name"));
                    String fullName = ((firstName == null ? "" : firstName.trim()) + " " + (lastName == null ? "" : lastName.trim())).trim();
                    if (fullName.isEmpty()) {
                        fullName = repairMojibake(rs.getString("title"));
                    }
                    if (fullName == null || fullName.isBlank()) {
                        long patientId = rs.getLong("patient_id");
                        fullName = rs.wasNull() ? "Evento" : "Paziente #" + patientId;
                    }
                    long rawPatientId = rs.getLong("patient_id");
                    Long patientId = rs.wasNull() ? null : rawPatientId;
                    result.add(new CalendarEventView(
                            rs.getLong("id"),
                            patientId,
                            rs.getLong("therapist_id"),
                            rs.getTimestamp("start_time").toLocalDateTime(),
                            rs.getTimestamp("end_time").toLocalDateTime(),
                            rs.getBoolean("all_day"),
                            rs.getString("notes"),
                            AppointmentState.valueOf(rs.getString("state")),
                            fullName,
                            rs.getString("phone")
                    ));
                }
            }

            return result;

        } catch (SQLException e) {
            throw new RuntimeException("Errore caricamento eventi calendario terapista", e);
        }
    }

    @Override
    public List<Appointment> findCancelledByTherapist(long therapistId) {
        String sql = """
            SELECT * FROM appointments
            WHERE therapist_id = ?
              AND state = 'CANCELLED'
            ORDER BY start_time DESC
        """;

        List<Appointment> result = new ArrayList<>();

        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, therapistId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }

            return result;

        } catch (SQLException e) {
            throw new RuntimeException("Errore caricamento appuntamenti cancellati", e);
        }
    }

    @Override
    public List<Long> findDistinctPatientIdsByTherapistInPeriod(long therapistId, LocalDateTime start, LocalDateTime end) {
        String sql = """
            SELECT DISTINCT patient_id
            FROM appointments
            WHERE therapist_id = ?
              AND state <> 'CANCELLED'
              AND patient_id IS NOT NULL
              AND start_time >= ?
              AND start_time < ?
            ORDER BY patient_id
        """;

        List<Long> result = new ArrayList<>();
        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, therapistId);
            ps.setTimestamp(2, Timestamp.valueOf(start));
            ps.setTimestamp(3, Timestamp.valueOf(end));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getLong("patient_id"));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("Errore caricamento pazienti trattati nel periodo", e);
        }
    }

    @Override
    public int countByPatientId(long patientId) {
        String sql = """
            SELECT COUNT(*) AS total
            FROM appointments
            WHERE patient_id = ?
        """;
        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, patientId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("total");
                }
                return 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Errore conteggio appuntamenti paziente", e);
        }
    }

    @Override
    public int detachPatientFromAppointments(long patientId, String fallbackTitle) {
        String sql = """
            UPDATE appointments
            SET patient_id = NULL,
                title = CASE
                    WHEN title IS NULL OR TRIM(title) = '' THEN ?
                    ELSE title
                END
            WHERE patient_id = ?
        """;
        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fallbackTitle);
            ps.setLong(2, patientId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Errore sgancio appuntamenti dal paziente", e);
        }
    }

    @Override
    public int deleteCancelledByTherapist(long therapistId) {
        String sql = """
            DELETE FROM appointments
            WHERE therapist_id = ?
              AND state = 'CANCELLED'
        """;

        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, therapistId);
            return ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Errore svuotamento cestino appuntamenti", e);
        }
    }

    @Override
    public int deleteCancelledOlderThanDays(long therapistId, int retentionDays) {
        String sql = """
            DELETE FROM appointments
            WHERE therapist_id = ?
              AND state = 'CANCELLED'
              AND cancelled_at IS NOT NULL
              AND TIMESTAMPDIFF(DAY, cancelled_at, NOW()) >= ?
        """;

        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, therapistId);
            ps.setInt(2, retentionDays);
            return ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Errore pulizia automatica cestino appuntamenti", e);
        }
    }

    @Override
    public void deleteById(long appointmentId) {
        String sql = "DELETE FROM appointments WHERE id = ?";

        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, appointmentId);
            int deletedRows = ps.executeUpdate();

            if (deletedRows == 0) {
                throw new RuntimeException("Nessun appuntamento eliminato, id non trovato: " + appointmentId);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Errore durante eliminazione appuntamento", e);
        }
    }

    private Appointment mapRow(ResultSet rs) throws SQLException {
        Appointment a = new Appointment();
        a.setId(rs.getLong("id"));
        long patientId = rs.getLong("patient_id");
        a.setPatientId(rs.wasNull() ? null : patientId);
        a.setTherapistId(rs.getLong("therapist_id"));
        a.setStart(rs.getTimestamp("start_time").toLocalDateTime());
        a.setEnd(rs.getTimestamp("end_time").toLocalDateTime());
        a.setAllDay(rs.getBoolean("all_day"));
        a.setTitle(rs.getString("title"));
        a.setNotes(rs.getString("notes"));
        a.setState(AppointmentState.valueOf(rs.getString("state")));
        Timestamp cancelledAt = rs.getTimestamp("cancelled_at");
        if (cancelledAt != null) {
            a.setCancelledAt(cancelledAt.toLocalDateTime());
        }
        return a;
    }

    private String repairMojibake(String value) {
        if (value == null || value.isBlank() || !looksLikeMojibake(value)) {
            return value;
        }
        String repaired = new String(value.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
        return looksLikeMojibake(repaired) ? value : repaired;
    }

    private boolean looksLikeMojibake(String value) {
        return value.contains("Ã") || value.contains("Â") || value.contains("â") || value.contains("�");
    }
}
