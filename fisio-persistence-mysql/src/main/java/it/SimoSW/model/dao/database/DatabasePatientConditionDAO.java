package it.SimoSW.model.dao.database;

import it.SimoSW.model.PatientCondition;
import it.SimoSW.model.ConditionCategory;
import it.SimoSW.model.dao.PatientConditionDAO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DatabasePatientConditionDAO implements PatientConditionDAO {

    private static final String INSERT_CONDITION = """
            INSERT INTO patient_conditions (anamnesis_id, category, code, label, status, notes)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

    private static final String FIND_BY_ANAMNESIS = """
            SELECT anamnesis_id, category, code, label, status, notes
            FROM patient_conditions
            WHERE anamnesis_id = ?
            ORDER BY id ASC
            """;

    @Override
    public void saveAll(long anamnesisId, List<PatientCondition> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return;
        }

        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement stmt = conn.prepareStatement(INSERT_CONDITION)) {

            for (PatientCondition condition : conditions) {
                stmt.setLong(1, anamnesisId);
                stmt.setString(2, condition.getCategory().name());
                stmt.setString(3, condition.getCode());
                stmt.setString(4, condition.getLabel());
                stmt.setString(5, condition.getStatus());
                stmt.setString(6, condition.getNotes());
                stmt.addBatch();
            }

            stmt.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException("Errore durante il salvataggio delle condizioni anamnestiche", e);
        }
    }

    @Override
    public List<PatientCondition> findByAnamnesisId(long anamnesisId) {
        List<PatientCondition> results = new ArrayList<>();

        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement stmt = conn.prepareStatement(FIND_BY_ANAMNESIS)) {
            stmt.setLong(1, anamnesisId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    PatientCondition condition = new PatientCondition();
                    condition.setAnamnesisId(rs.getLong("anamnesis_id"));
                    condition.setCategory(ConditionCategory.valueOf(rs.getString("category")));
                    condition.setCode(rs.getString("code"));
                    condition.setLabel(rs.getString("label"));
                    condition.setStatus(rs.getString("status"));
                    condition.setNotes(rs.getString("notes"));
                    results.add(condition);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Errore durante il recupero delle condizioni anamnestiche", e);
        }

        return results;
    }
}
