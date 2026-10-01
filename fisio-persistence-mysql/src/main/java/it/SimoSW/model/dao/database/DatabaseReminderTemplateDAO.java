package it.SimoSW.model.dao.database;

import it.SimoSW.model.dao.ReminderTemplateDAO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class DatabaseReminderTemplateDAO implements ReminderTemplateDAO {

    private static final String FIND_BY_THERAPIST = """
            SELECT reminder_template
            FROM therapist_reminder_templates
            WHERE therapist_id = ?
            """;

    private static final String UPSERT_TEMPLATE = """
            INSERT INTO therapist_reminder_templates (therapist_id, reminder_template)
            VALUES (?, ?)
            ON DUPLICATE KEY UPDATE
                reminder_template = VALUES(reminder_template),
                updated_at = CURRENT_TIMESTAMP
            """;

    @Override
    public Optional<String> findTemplateByTherapistId(long therapistId) {
        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement stmt = conn.prepareStatement(FIND_BY_THERAPIST)) {

            stmt.setLong(1, therapistId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.ofNullable(rs.getString("reminder_template"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Errore durante il recupero del template reminder", e);
        }
    }

    @Override
    public void saveTemplate(long therapistId, String template) {
        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement stmt = conn.prepareStatement(UPSERT_TEMPLATE)) {

            stmt.setLong(1, therapistId);
            stmt.setString(2, template);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Errore durante il salvataggio del template reminder", e);
        }
    }
}
