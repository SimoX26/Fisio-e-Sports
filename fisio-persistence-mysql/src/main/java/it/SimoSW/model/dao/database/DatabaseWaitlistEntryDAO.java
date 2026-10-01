package it.SimoSW.model.dao.database;

import it.SimoSW.model.WaitlistEntry;
import it.SimoSW.model.dao.WaitlistEntryDAO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class DatabaseWaitlistEntryDAO implements WaitlistEntryDAO {

    private static final String INSERT_ENTRY = """
            INSERT INTO waitlist_entries (therapist_id, full_name, phone)
            VALUES (?, ?, ?)
            """;

    private static final String FIND_ALL_BY_THERAPIST = """
            SELECT id, therapist_id, full_name, phone, created_at
            FROM waitlist_entries
            WHERE therapist_id = ?
            ORDER BY created_at ASC, id ASC
            """;

    private static final String DELETE_BY_ID_AND_THERAPIST = """
            DELETE FROM waitlist_entries
            WHERE id = ? AND therapist_id = ?
            """;

    @Override
    public WaitlistEntry insert(WaitlistEntry entry) {
        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement stmt = conn.prepareStatement(INSERT_ENTRY, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setLong(1, entry.getTherapistId());
            stmt.setString(2, entry.getFullName());
            stmt.setString(3, entry.getPhone());
            stmt.executeUpdate();

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    entry.setId(keys.getLong(1));
                }
            }

            return entry;
        } catch (SQLException e) {
            throw new RuntimeException("Errore durante il salvataggio della lista di attesa", e);
        }
    }

    @Override
    public List<WaitlistEntry> findAllByTherapist(long therapistId) {
        List<WaitlistEntry> entries = new ArrayList<>();

        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement stmt = conn.prepareStatement(FIND_ALL_BY_THERAPIST)) {

            stmt.setLong(1, therapistId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    entries.add(mapRow(rs));
                }
            }

            return entries;
        } catch (SQLException e) {
            throw new RuntimeException("Errore durante il recupero della lista di attesa", e);
        }
    }

    @Override
    public void deleteByIdAndTherapist(long id, long therapistId) {
        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement stmt = conn.prepareStatement(DELETE_BY_ID_AND_THERAPIST)) {

            stmt.setLong(1, id);
            stmt.setLong(2, therapistId);

            int deletedRows = stmt.executeUpdate();
            if (deletedRows == 0) {
                throw new RuntimeException("Contatto in lista di attesa non trovato");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Errore durante la rimozione dalla lista di attesa", e);
        }
    }

    private WaitlistEntry mapRow(ResultSet rs) throws SQLException {
        WaitlistEntry entry = new WaitlistEntry();
        entry.setId(rs.getLong("id"));
        entry.setTherapistId(rs.getLong("therapist_id"));
        entry.setFullName(rs.getString("full_name"));
        entry.setPhone(rs.getString("phone"));

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            entry.setCreatedAt(createdAt.toLocalDateTime());
        }

        return entry;
    }
}
