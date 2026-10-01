package it.SimoSW.backend;

import com.sun.net.httpserver.HttpExchange;
import it.SimoSW.model.ConditionCategory;
import it.SimoSW.model.Patient;
import it.SimoSW.model.PatientAnamnesis;
import it.SimoSW.model.PatientCondition;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class PatientForm {
    private final Map<String, String> values;

    private PatientForm(Map<String, String> values) {
        this.values = values;
    }

    static PatientForm read(HttpExchange exchange) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.startsWith("application/x-www-form-urlencoded")) {
            throw new UnsupportedOperationException("unsupported_media_type");
        }
        byte[] body = exchange.getRequestBody().readNBytes(65537);
        if (body.length > 65536) throw new IllegalArgumentException("body_too_large");
        Map<String, String> fields = new HashMap<>();
        for (String part : new String(body, StandardCharsets.UTF_8).split("&")) {
            int separator = part.indexOf('=');
            if (separator > 0) {
                fields.put(URLDecoder.decode(part.substring(0, separator), StandardCharsets.UTF_8),
                        URLDecoder.decode(part.substring(separator + 1), StandardCharsets.UTF_8));
            }
        }
        return new PatientForm(fields);
    }

    Patient patient() {
        String fullName = value("fullName").replaceAll("\\s+", " ");
        String[] name = fullName.split(" ", 2);
        String firstName = name[0];
        String lastName = name.length > 1 ? name[1] : "";
        String email = value("email");
        String phone = value("phone");
        if (firstName.isBlank() || firstName.length() > 100 || lastName.length() > 100
                || email.length() > 150 || phone.length() > 20) {
            throw new IllegalArgumentException("invalid_patient");
        }
        Patient patient = new Patient();
        patient.setFirstName(firstName);
        patient.setLastName(lastName);
        patient.setEmail(email);
        patient.setPhone(phone);
        return patient;
    }

    long mergeTargetId() {
        String raw = value("mergeTargetId");
        if (raw.isEmpty()) return 0;
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    boolean hasAnamnesisData() {
        return values.entrySet().stream().anyMatch(entry -> !entry.getKey().equals("assessmentDate")
                && !entry.getKey().equals("fullName") && !entry.getKey().equals("email")
                && !entry.getKey().equals("phone") && !entry.getKey().equals("mergeTargetId")
                && !entry.getValue().isBlank());
    }

    PatientAnamnesis anamnesis() {
        PatientAnamnesis a = new PatientAnamnesis();
        a.setAssessmentDate(value("assessmentDate").isEmpty() ? LocalDate.now() : LocalDate.parse(value("assessmentDate")));
        a.setChiefComplaint(value("chiefComplaint"));
        a.setPainLocation(value("painLocation"));
        a.setPainQuality(value("painQuality"));
        a.setAssociatedSymptoms(value("associatedSymptoms"));
        a.setOnsetType(optionalUpper("onsetType"));
        a.setOnsetContext(value("onsetContext"));
        a.setDisabling(booleanValue("isDisabling"));
        a.setPainFrequency(optionalUpper("painFrequency"));
        a.setPainProgression(optionalUpper("painProgression"));
        a.setPainWithMovement(optionalUpper("painWithMovement"));
        a.setPainWithRest(optionalUpper("painWithRest"));
        a.setNightPain(booleanValue("nightPain"));
        a.setMorningPain(booleanValue("morningPain"));
        a.setPainIntensity(integerValue("painIntensity", 0, 10));
        a.setUsesPainMeds(booleanValue("usesPainMeds"));
        a.setPainMedsEffect(optionalUpper("painMedsEffect"));
        a.setClinicalTests(value("clinicalTests"));
        a.setSpecialistVisits(value("specialistVisits"));
        a.setPreviousTreatments(value("previousTreatments"));
        a.setPathologyHistory(value("pathologyHistory"));
        a.setCurrentRegularDrugs(value("currentRegularDrugs"));
        a.setSurgeryHistory(value("surgeryHistory"));
        a.setTraumaHistory(value("traumaHistory"));
        a.setDevicesHistory(value("devicesHistory"));
        a.setChewingDisorders(booleanValue("chewingDisorders"));
        a.setMajorInfectionsHistory(value("majorInfectionsHistory"));
        a.setFamilyHistory(value("familyHistory"));
        a.setHeightCm(decimalValue("heightCm"));
        a.setWeightKg(decimalValue("weightKg"));
        a.setLifestyle(optionalUpper("lifestyle"));
        a.setSportPractice(value("sportPractice"));
        a.setSubstanceUse(value("substanceUse"));
        a.setSleepQuality(integerValue("sleepQuality", 0, 4));
        a.setStressLevel(integerValue("stressLevel", 0, 4));
        a.setDietQuality(optionalUpper("dietQuality"));
        a.setFemaleCycleNotes(value("femaleCycleNotes"));
        String notes = value("freeNotesJson");
        a.setFreeNotesJson(notes.isEmpty() ? null : "{\"note\":" + ApiJson.quote(notes) + "}");
        return a;
    }

    List<PatientCondition> conditions() {
        List<PatientCondition> conditions = new ArrayList<>();
        addConditions(conditions, "conditionsPathology", ConditionCategory.PATHOLOGY);
        addConditions(conditions, "conditionsSymptom", ConditionCategory.SYMPTOM);
        addConditions(conditions, "conditionsFamilyHistory", ConditionCategory.FAMILY_HISTORY);
        addConditions(conditions, "conditionsAllergy", ConditionCategory.ALLERGY);
        addConditions(conditions, "conditionsDrug", ConditionCategory.DRUG);
        addConditions(conditions, "conditionsSystemReview", ConditionCategory.SYSTEM_REVIEW);
        addConditions(conditions, "conditionsOther", ConditionCategory.OTHER);
        return conditions;
    }

    private void addConditions(List<PatientCondition> target, String field, ConditionCategory category) {
        for (String part : value(field).split("[,;\\n]+")) {
            String label = part.trim();
            if (label.isEmpty()) continue;
            PatientCondition condition = new PatientCondition();
            condition.setCategory(category);
            condition.setLabel(label);
            condition.setStatus("PRESENT");
            target.add(condition);
        }
    }

    private String value(String name) {
        return values.getOrDefault(name, "").trim();
    }

    private String optionalUpper(String name) {
        String value = value(name);
        return value.isEmpty() ? null : value.toUpperCase(java.util.Locale.ROOT);
    }

    private Boolean booleanValue(String name) {
        return switch (value(name).toLowerCase(java.util.Locale.ROOT)) {
            case "" -> null;
            case "si", "yes", "true", "1" -> true;
            case "no", "false", "0" -> false;
            default -> throw new IllegalArgumentException("invalid_boolean");
        };
    }

    private Integer integerValue(String name, int min, int max) {
        String raw = value(name);
        if (raw.isEmpty()) return null;
        int number = Integer.parseInt(raw);
        if (number < min || number > max) throw new IllegalArgumentException("invalid_number");
        return number;
    }

    private BigDecimal decimalValue(String name) {
        String raw = value(name).replace(',', '.');
        return raw.isEmpty() ? null : new BigDecimal(raw);
    }
}
