package it.SimoSW.model.dao.database;

import it.SimoSW.model.PatientAnamnesis;
import it.SimoSW.model.dao.PatientAnamnesisDAO;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Optional;

public class DatabasePatientAnamnesisDAO implements PatientAnamnesisDAO {

    private static final String INSERT_ANAMNESIS = """
            INSERT INTO patient_anamneses (
                patient_id,
                therapist_id,
                assessment_date,
                chief_complaint,
                pain_location,
                pain_quality,
                associated_symptoms,
                onset_type,
                onset_context,
                is_disabling,
                pain_frequency,
                pain_progression,
                pain_with_movement,
                pain_with_rest,
                night_pain,
                morning_pain,
                pain_intensity,
                uses_pain_meds,
                pain_meds_effect,
                clinical_tests,
                specialist_visits,
                previous_treatments,
                pathology_history,
                current_regular_drugs,
                surgery_history,
                trauma_history,
                devices_history,
                chewing_disorders,
                major_infections_history,
                family_history,
                height_cm,
                weight_kg,
                lifestyle,
                sport_practice,
                substance_use,
                sleep_quality,
                stress_level,
                diet_quality,
                female_cycle_notes,
                free_notes_json
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String FIND_LATEST_BY_PATIENT = """
            SELECT id,
                   patient_id,
                   therapist_id,
                   assessment_date,
                   chief_complaint,
                   pain_location,
                   pain_quality,
                   associated_symptoms,
                   onset_type,
                   onset_context,
                   is_disabling,
                   pain_frequency,
                   pain_progression,
                   pain_with_movement,
                   pain_with_rest,
                   night_pain,
                   morning_pain,
                   pain_intensity,
                   uses_pain_meds,
                   pain_meds_effect,
                   clinical_tests,
                   specialist_visits,
                   previous_treatments,
                   pathology_history,
                   current_regular_drugs,
                   surgery_history,
                   trauma_history,
                   devices_history,
                   chewing_disorders,
                   major_infections_history,
                   family_history,
                   height_cm,
                   weight_kg,
                   lifestyle,
                   sport_practice,
                   substance_use,
                   sleep_quality,
                   stress_level,
                   diet_quality,
                   female_cycle_notes,
                   free_notes_json
            FROM patient_anamneses
            WHERE patient_id = ?
            ORDER BY assessment_date DESC, id DESC
            LIMIT 1
            """;

    @Override
    public PatientAnamnesis save(PatientAnamnesis anamnesis) {
        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement stmt = conn.prepareStatement(INSERT_ANAMNESIS, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setLong(1, anamnesis.getPatientId());
            stmt.setLong(2, anamnesis.getTherapistId());
            stmt.setDate(3, Date.valueOf(anamnesis.getAssessmentDate()));
            stmt.setString(4, anamnesis.getChiefComplaint());
            stmt.setString(5, anamnesis.getPainLocation());
            stmt.setString(6, anamnesis.getPainQuality());
            stmt.setString(7, anamnesis.getAssociatedSymptoms());
            stmt.setString(8, anamnesis.getOnsetType());
            stmt.setString(9, anamnesis.getOnsetContext());
            setNullableBoolean(stmt, 10, anamnesis.getDisabling());
            stmt.setString(11, anamnesis.getPainFrequency());
            stmt.setString(12, anamnesis.getPainProgression());
            stmt.setString(13, anamnesis.getPainWithMovement());
            stmt.setString(14, anamnesis.getPainWithRest());
            setNullableBoolean(stmt, 15, anamnesis.getNightPain());
            setNullableBoolean(stmt, 16, anamnesis.getMorningPain());
            setNullableInteger(stmt, 17, anamnesis.getPainIntensity());
            setNullableBoolean(stmt, 18, anamnesis.getUsesPainMeds());
            stmt.setString(19, anamnesis.getPainMedsEffect());
            stmt.setString(20, anamnesis.getClinicalTests());
            stmt.setString(21, anamnesis.getSpecialistVisits());
            stmt.setString(22, anamnesis.getPreviousTreatments());
            stmt.setString(23, anamnesis.getPathologyHistory());
            stmt.setString(24, anamnesis.getCurrentRegularDrugs());
            stmt.setString(25, anamnesis.getSurgeryHistory());
            stmt.setString(26, anamnesis.getTraumaHistory());
            stmt.setString(27, anamnesis.getDevicesHistory());
            setNullableBoolean(stmt, 28, anamnesis.getChewingDisorders());
            stmt.setString(29, anamnesis.getMajorInfectionsHistory());
            stmt.setString(30, anamnesis.getFamilyHistory());
            stmt.setBigDecimal(31, anamnesis.getHeightCm());
            stmt.setBigDecimal(32, anamnesis.getWeightKg());
            stmt.setString(33, anamnesis.getLifestyle());
            stmt.setString(34, anamnesis.getSportPractice());
            stmt.setString(35, anamnesis.getSubstanceUse());
            setNullableInteger(stmt, 36, anamnesis.getSleepQuality());
            setNullableInteger(stmt, 37, anamnesis.getStressLevel());
            stmt.setString(38, anamnesis.getDietQuality());
            stmt.setString(39, anamnesis.getFemaleCycleNotes());
            stmt.setString(40, anamnesis.getFreeNotesJson());

            stmt.executeUpdate();

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    anamnesis.setId(keys.getLong(1));
                }
            }

            return anamnesis;
        } catch (SQLException e) {
            throw new RuntimeException("Errore durante il salvataggio dell'anamnesi: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<PatientAnamnesis> findLatestByPatientId(long patientId) {
        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement stmt = conn.prepareStatement(FIND_LATEST_BY_PATIENT)) {

            stmt.setLong(1, patientId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Errore durante il recupero dell'anamnesi", e);
        }
    }

    private PatientAnamnesis mapRow(ResultSet rs) throws SQLException {
        PatientAnamnesis anamnesis = new PatientAnamnesis();
        anamnesis.setId(rs.getLong("id"));
        anamnesis.setPatientId(rs.getLong("patient_id"));
        anamnesis.setTherapistId(rs.getLong("therapist_id"));
        anamnesis.setAssessmentDate(rs.getDate("assessment_date").toLocalDate());
        anamnesis.setChiefComplaint(rs.getString("chief_complaint"));
        anamnesis.setPainLocation(rs.getString("pain_location"));
        anamnesis.setPainQuality(rs.getString("pain_quality"));
        anamnesis.setAssociatedSymptoms(rs.getString("associated_symptoms"));
        anamnesis.setOnsetType(rs.getString("onset_type"));
        anamnesis.setOnsetContext(rs.getString("onset_context"));
        anamnesis.setDisabling(getNullableBoolean(rs, "is_disabling"));
        anamnesis.setPainFrequency(rs.getString("pain_frequency"));
        anamnesis.setPainProgression(rs.getString("pain_progression"));
        anamnesis.setPainWithMovement(rs.getString("pain_with_movement"));
        anamnesis.setPainWithRest(rs.getString("pain_with_rest"));
        anamnesis.setNightPain(getNullableBoolean(rs, "night_pain"));
        anamnesis.setMorningPain(getNullableBoolean(rs, "morning_pain"));
        anamnesis.setPainIntensity(getNullableInteger(rs, "pain_intensity"));
        anamnesis.setUsesPainMeds(getNullableBoolean(rs, "uses_pain_meds"));
        anamnesis.setPainMedsEffect(rs.getString("pain_meds_effect"));
        anamnesis.setClinicalTests(rs.getString("clinical_tests"));
        anamnesis.setSpecialistVisits(rs.getString("specialist_visits"));
        anamnesis.setPreviousTreatments(rs.getString("previous_treatments"));
        anamnesis.setPathologyHistory(rs.getString("pathology_history"));
        anamnesis.setCurrentRegularDrugs(rs.getString("current_regular_drugs"));
        anamnesis.setSurgeryHistory(rs.getString("surgery_history"));
        anamnesis.setTraumaHistory(rs.getString("trauma_history"));
        anamnesis.setDevicesHistory(rs.getString("devices_history"));
        anamnesis.setChewingDisorders(getNullableBoolean(rs, "chewing_disorders"));
        anamnesis.setMajorInfectionsHistory(rs.getString("major_infections_history"));
        anamnesis.setFamilyHistory(rs.getString("family_history"));
        anamnesis.setHeightCm(rs.getBigDecimal("height_cm"));
        anamnesis.setWeightKg(rs.getBigDecimal("weight_kg"));
        anamnesis.setLifestyle(rs.getString("lifestyle"));
        anamnesis.setSportPractice(rs.getString("sport_practice"));
        anamnesis.setSubstanceUse(rs.getString("substance_use"));
        anamnesis.setSleepQuality(getNullableInteger(rs, "sleep_quality"));
        anamnesis.setStressLevel(getNullableInteger(rs, "stress_level"));
        anamnesis.setDietQuality(rs.getString("diet_quality"));
        anamnesis.setFemaleCycleNotes(rs.getString("female_cycle_notes"));
        anamnesis.setFreeNotesJson(rs.getString("free_notes_json"));
        return anamnesis;
    }

    private void setNullableBoolean(PreparedStatement stmt, int index, Boolean value) throws SQLException {
        if (value == null) {
            stmt.setNull(index, Types.BOOLEAN);
            return;
        }
        stmt.setBoolean(index, value);
    }

    private Boolean getNullableBoolean(ResultSet rs, String column) throws SQLException {
        boolean value = rs.getBoolean(column);
        return rs.wasNull() ? null : value;
    }

    private void setNullableInteger(PreparedStatement stmt, int index, Integer value) throws SQLException {
        if (value == null) {
            stmt.setNull(index, Types.INTEGER);
            return;
        }
        stmt.setInt(index, value);
    }

    private Integer getNullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
