package it.SimoSW.controller.graphic;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import it.SimoSW.util.bootstrap.ApplicationInitializer;
import it.SimoSW.controller.application.AddressBookController;
import it.SimoSW.model.ConditionCategory;
import it.SimoSW.model.PatientAnamnesis;
import it.SimoSW.model.PatientCondition;
import it.SimoSW.model.Patient;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.math.BigDecimal;
import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@WebServlet("/address-book")
public class AddressBookServlet extends HttpServlet {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private AddressBookController addressBookController;

    private long resolveTherapistId(HttpServletRequest request) {
        Object username = request.getSession().getAttribute("loggedUser");
        return addressBookController.resolveTherapistIdByUsername(username == null ? null : username.toString());
    }

    @Override
    public void init() {
        ApplicationInitializer initializer =
                (ApplicationInitializer) getServletContext()
                        .getAttribute("appInitializer");

        this.addressBookController = initializer.getAddressBookController();
    }

    /* =========================
       GET → visualizzazione
       ========================= */
    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response)
            throws ServletException, IOException {
        String action = normalizeOptional(request.getParameter("action"));
        if ("anamnesis-details".equals(action)) {
            sendAnamnesisDetails(request, response);
            return;
        }
        if ("merge-candidates".equals(action)) {
            sendMergeCandidates(request, response);
            return;
        }

        List<Patient> patients = Collections.emptyList();
        String query = request.getParameter("q");
        String treatedDateParam = normalizeOptional(request.getParameter("treatedDate"));
        String treatedMonthParam = normalizeOptional(request.getParameter("treatedMonth"));
        String nameSort = normalizeOptional(request.getParameter("sortName")).toLowerCase();
        String createdSort = normalizeOptional(request.getParameter("sortCreated")).toLowerCase();

        try {
            if (!treatedDateParam.isEmpty()) {
                LocalDate treatedDate = parseDate(treatedDateParam);
                String loggedUser = normalizeOptional((String) request.getSession().getAttribute("loggedUser"));
                if (loggedUser.isEmpty()) {
                    throw new IllegalArgumentException("Sessione terapista non valida");
                }
                long therapistId = addressBookController.resolveTherapistIdByUsername(loggedUser);
                patients = addressBookController.getPatientsTreatedOnDate(therapistId, treatedDate);
                request.setAttribute("treatedDateLabel", treatedDate.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale.ITALIAN)));
                request.setAttribute("treatedDateParam", treatedDate.toString());
            } else if (!treatedMonthParam.isEmpty()) {
                YearMonth treatedMonth = parseYearMonth(treatedMonthParam);
                String loggedUser = normalizeOptional((String) request.getSession().getAttribute("loggedUser"));
                if (loggedUser.isEmpty()) {
                    throw new IllegalArgumentException("Sessione terapista non valida");
                }
                long therapistId = addressBookController.resolveTherapistIdByUsername(loggedUser);
                patients = addressBookController.getPatientsTreatedInMonth(therapistId, treatedMonth);
                request.setAttribute("treatedMonthLabel", treatedMonth.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", java.util.Locale.ITALIAN)));
                request.setAttribute("treatedMonthParam", treatedMonth.toString());
            } else {
                patients = addressBookController.searchPatients(
                        query != null ? query : "", resolveTherapistId(request)
                );
            }
            if ("asc".equals(nameSort) || "desc".equals(nameSort)) {
                Comparator<Patient> byName = Comparator.comparing(
                        p -> p.getFullName().toLowerCase()
                );
                if ("desc".equals(nameSort)) {
                    byName = byName.reversed();
                }
                patients.sort(byName);
                createdSort = "";
            } else {
                Comparator<Patient> byCreatedAt = Comparator.comparing(Patient::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                if (!"asc".equals(createdSort)) {
                    patients.sort(byCreatedAt.reversed());
                    createdSort = "desc";
                } else {
                    patients.sort(byCreatedAt);
                }
            }
        } catch (RuntimeException ex) {
            request.setAttribute("error", "Impossibile caricare la rubrica in questo momento.");
        }

        request.setAttribute("patients", patients);
        request.setAttribute("nameSort", nameSort);
        request.setAttribute("createdSort", createdSort);
        request.getRequestDispatcher("/WEB-INF/jsp/therapist/addressBook.jsp")
                .forward(request, response);
    }

    /* =========================
       POST → azioni
       ========================= */
    @Override
    protected void doPost(HttpServletRequest request,
                          HttpServletResponse response)
            throws ServletException, IOException {

        String action = request.getParameter("action");
        String redirectPath = "/address-book";

        try {
            switch (action) {

                case "create" -> {
                    createPatient(request);
                    PostSubmitNavigationGuard.blockFormPageOnce(request, "/address-book/create", "/address-book?lockBack=1");
                    redirectPath = "dashboard".equals(normalizeOptional(request.getParameter("returnTo")))
                            ? "/dashboard?patientCreated=1"
                            : "/address-book?created=1&lockBack=1";
                }
                case "update" -> {
                    updatePatient(request);
                    redirectPath = "/address-book?updated=1";
                }
                case "delete" -> deletePatient(request);
                case "activate" -> changeState(request, "activate");
                case "deactivate" -> changeState(request, "deactivate");
                case "archive" -> changeState(request, "archive");

                default -> throw new IllegalArgumentException("Unknown action");
            }

            response.sendRedirect(request.getContextPath() + redirectPath);

        } catch (RuntimeException ex) {
            request.setAttribute("error", ex.getMessage());
            doGet(request, response);
        }
    }

    /* =========================
       Metodi di supporto
       ========================= */

    private void createPatient(HttpServletRequest request) {
        Patient p = new Patient();
        p.setId(Long.parseLong(request.getParameter("id")));
        PatientNameParts patientName = extractPatientName(request);
        p.setFirstName(patientName.firstName());
        p.setLastName(patientName.lastName());
        p.setEmail(normalizeOptional(request.getParameter("email")));
        p.setPhone(normalizeOptional(request.getParameter("phone")));

        addressBookController.registerPatient(p, resolveTherapistId(request));
    }

    private void updatePatient(HttpServletRequest request) {
        Patient p = new Patient();
        p.setId(Long.parseLong(request.getParameter("id")));
        PatientNameParts patientName = extractPatientName(request);
        p.setFirstName(patientName.firstName());
        p.setLastName(patientName.lastName());
        p.setEmail(normalizeOptional(request.getParameter("email")));
        p.setPhone(normalizeOptional(request.getParameter("phone")));

        long therapistId = resolveTherapistId(request);
        addressBookController.updatePatientProfile(p, therapistId);

        long mergeTargetId = parseLongOrZero(request.getParameter("mergeTargetId"));
        if (mergeTargetId > 0) {
            addressBookController.mergePatients(p.getId(), mergeTargetId, therapistId);
            return;
        }

        PatientAnamnesis anamnesis = parseAnamnesis(request);
        List<PatientCondition> conditions = parseConditions(request);

        if (!hasAnamnesisData(anamnesis, conditions)) {
            return;
        }

        Object loggedUser = request.getSession().getAttribute("loggedUser");
        if (loggedUser == null) {
            throw new IllegalArgumentException("Sessione terapista non valida");
        }
        String therapistUsername = String.valueOf(loggedUser).trim();
        if (therapistUsername.isEmpty()) {
            throw new IllegalArgumentException("Sessione terapista non valida");
        }

        addressBookController.savePatientAnamnesis(p.getId(), therapistUsername, anamnesis, conditions);
    }

    private void changeState(HttpServletRequest request, String state) {
        long patientId = Long.parseLong(request.getParameter("id"));

        switch (state) {
            case "activate" ->
                    addressBookController.activatePatient(patientId, resolveTherapistId(request));
            case "deactivate" ->
                    addressBookController.deactivatePatient(patientId, resolveTherapistId(request));
            case "archive" ->
                    addressBookController.archivePatient(patientId, resolveTherapistId(request));
        }
    }

    private void deletePatient(HttpServletRequest request) {
        long patientId = Long.parseLong(request.getParameter("id"));
        boolean forceDelete = "1".equals(normalizeOptional(request.getParameter("forceDeleteWithLinkedAppointments")));
        addressBookController.deletePatient(patientId, forceDelete, resolveTherapistId(request));
    }

    private void sendAnamnesisDetails(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String patientIdRaw = normalizeOptional(request.getParameter("id"));
        if (patientIdRaw.isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Parametro id mancante");
            return;
        }

        long patientId;
        try {
            patientId = Long.parseLong(patientIdRaw);
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Parametro id non valido");
            return;
        }

        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        ObjectNode anamnesisNode = root.putObject("anamnesis");

        try {
            long therapistId = resolveTherapistId(request);
            Optional<PatientAnamnesis> anamnesisOpt = addressBookController.getLatestAnamnesisByPatientId(patientId, therapistId);
            if (anamnesisOpt.isPresent()) {
                PatientAnamnesis anamnesis = anamnesisOpt.get();
                List<PatientCondition> conditions = addressBookController.getConditionsByAnamnesisId(patientId, anamnesis.getId(), therapistId);
                fillAnamnesisNode(anamnesisNode, anamnesis, conditions);
            }
        } catch (RuntimeException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
            return;
        }

        response.setContentType("application/json; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        OBJECT_MAPPER.writeValue(response.getWriter(), root);
    }

    private void sendMergeCandidates(HttpServletRequest request, HttpServletResponse response) throws IOException {
        long sourceId = parseRequiredLong(request.getParameter("id"), "Parametro id non valido");
        PatientNameParts patientName = extractPatientName(request);
        List<Patient> candidates = addressBookController.findMergeCandidates(sourceId, patientName.firstName(), patientName.lastName(), resolveTherapistId(request));

        List<Map<String, Object>> payload = new ArrayList<>();
        for (Patient candidate : candidates) {
            Map<String, Object> row = new java.util.HashMap<>();
            row.put("id", candidate.getId());
            row.put("fullName", candidate.getFullName());
            row.put("phone", normalizeOptional(candidate.getPhone()));
            row.put("createdDate", candidate.getCreatedDateLabel());
            payload.add(row);
        }

        response.setContentType("application/json; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        OBJECT_MAPPER.writeValue(response.getWriter(), payload);
    }

    private void fillAnamnesisNode(ObjectNode target, PatientAnamnesis a, List<PatientCondition> conditions) {
        put(target, "assessmentDate", a.getAssessmentDate() == null ? "" : a.getAssessmentDate().toString());
        put(target, "chiefComplaint", a.getChiefComplaint());
        put(target, "painLocation", a.getPainLocation());
        put(target, "painQuality", a.getPainQuality());
        put(target, "associatedSymptoms", a.getAssociatedSymptoms());
        put(target, "onsetType", a.getOnsetType());
        put(target, "onsetContext", a.getOnsetContext());
        put(target, "isDisabling", booleanToFormValue(a.getDisabling()));
        put(target, "painFrequency", a.getPainFrequency());
        put(target, "painProgression", a.getPainProgression());
        put(target, "painWithMovement", a.getPainWithMovement());
        put(target, "painWithRest", a.getPainWithRest());
        put(target, "nightPain", booleanToFormValue(a.getNightPain()));
        put(target, "morningPain", booleanToFormValue(a.getMorningPain()));
        put(target, "painIntensity", a.getPainIntensity() == null ? "" : String.valueOf(a.getPainIntensity()));
        put(target, "usesPainMeds", booleanToFormValue(a.getUsesPainMeds()));
        put(target, "painMedsEffect", a.getPainMedsEffect());
        put(target, "clinicalTests", a.getClinicalTests());
        put(target, "specialistVisits", a.getSpecialistVisits());
        put(target, "previousTreatments", a.getPreviousTreatments());
        put(target, "pathologyHistory", a.getPathologyHistory());
        put(target, "currentRegularDrugs", a.getCurrentRegularDrugs());
        put(target, "surgeryHistory", a.getSurgeryHistory());
        put(target, "traumaHistory", a.getTraumaHistory());
        put(target, "devicesHistory", a.getDevicesHistory());
        put(target, "chewingDisorders", booleanToFormValue(a.getChewingDisorders()));
        put(target, "majorInfectionsHistory", a.getMajorInfectionsHistory());
        put(target, "familyHistory", a.getFamilyHistory());
        put(target, "heightCm", a.getHeightCm() == null ? "" : a.getHeightCm().toPlainString());
        put(target, "weightKg", a.getWeightKg() == null ? "" : a.getWeightKg().toPlainString());
        put(target, "lifestyle", a.getLifestyle());
        put(target, "sportPractice", a.getSportPractice());
        put(target, "substanceUse", a.getSubstanceUse());
        put(target, "sleepQuality", a.getSleepQuality() == null ? "" : String.valueOf(a.getSleepQuality()));
        put(target, "stressLevel", a.getStressLevel() == null ? "" : String.valueOf(a.getStressLevel()));
        put(target, "dietQuality", a.getDietQuality());
        put(target, "femaleCycleNotes", a.getFemaleCycleNotes());
        put(target, "freeNotesJson", toDisplayFreeNotes(a.getFreeNotesJson()));

        Map<ConditionCategory, StringBuilder> groupedConditions = new EnumMap<>(ConditionCategory.class);
        if (conditions != null) {
            for (PatientCondition condition : conditions) {
                if (condition == null || condition.getCategory() == null) {
                    continue;
                }
                String label = normalizeOptional(condition.getLabel());
                if (label.isEmpty()) {
                    continue;
                }
                StringBuilder bucket = groupedConditions.computeIfAbsent(condition.getCategory(), key -> new StringBuilder());
                if (bucket.length() > 0) {
                    bucket.append('\n');
                }
                bucket.append(label);
            }
        }

        put(target, "conditionsPathology", groupedConditions.getOrDefault(ConditionCategory.PATHOLOGY, new StringBuilder()).toString());
        put(target, "conditionsSymptom", groupedConditions.getOrDefault(ConditionCategory.SYMPTOM, new StringBuilder()).toString());
        put(target, "conditionsFamilyHistory", groupedConditions.getOrDefault(ConditionCategory.FAMILY_HISTORY, new StringBuilder()).toString());
        put(target, "conditionsAllergy", groupedConditions.getOrDefault(ConditionCategory.ALLERGY, new StringBuilder()).toString());
        put(target, "conditionsDrug", groupedConditions.getOrDefault(ConditionCategory.DRUG, new StringBuilder()).toString());
        put(target, "conditionsSystemReview", groupedConditions.getOrDefault(ConditionCategory.SYSTEM_REVIEW, new StringBuilder()).toString());
        put(target, "conditionsOther", groupedConditions.getOrDefault(ConditionCategory.OTHER, new StringBuilder()).toString());
    }

    private String booleanToFormValue(Boolean value) {
        if (value == null) {
            return "";
        }
        return value ? "si" : "no";
    }

    private PatientNameParts extractPatientName(HttpServletRequest request) {
        String fullName = normalizeOptional(request.getParameter("fullName"));
        if (!fullName.isEmpty()) {
            return splitPatientName(fullName);
        }

        String firstName = normalizeRequired(request.getParameter("firstName"), "Il nome e cognome del paziente è obbligatorio");
        String lastName = normalizeOptional(request.getParameter("lastName"));
        return new PatientNameParts(firstName, lastName);
    }

    private PatientNameParts splitPatientName(String fullNameRaw) {
        String fullName = normalizeRequired(fullNameRaw, "Il nome e cognome del paziente è obbligatorio");
        String[] parts = fullName.split("\\s+", 2);
        String firstName = parts[0];
        String lastName = parts.length > 1 ? parts[1].trim() : "";
        return new PatientNameParts(firstName, lastName);
    }

    private void put(ObjectNode target, String field, String value) {
        target.put(field, value == null ? "" : value);
    }

    private String toDisplayFreeNotes(String rawValue) {
        String normalized = normalizeOptional(rawValue);
        if (normalized.isEmpty()) {
            return "";
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(normalized);
            if (node != null && node.isObject()) {
                JsonNode noteNode = node.get("note");
                if (noteNode != null && noteNode.isTextual()) {
                    return noteNode.asText();
                }

                StringBuilder text = new StringBuilder();
                node.fields().forEachRemaining(entry -> {
                    JsonNode value = entry.getValue();
                    String valueText = value == null || value.isNull() ? "" : value.asText();
                    if (valueText.isEmpty()) {
                        return;
                    }
                    if (text.length() > 0) {
                        text.append('\n');
                    }
                    text.append(entry.getKey()).append(": ").append(valueText);
                });
                if (text.length() > 0) {
                    return text.toString();
                }
            }
            return normalized;
        } catch (JsonProcessingException e) {
            return normalized;
        }
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    private static final class PatientNameParts {
        private final String firstName;
        private final String lastName;

        private PatientNameParts(String firstName, String lastName) {
            this.firstName = firstName;
            this.lastName = lastName;
        }

        private String firstName() {
            return firstName;
        }

        private String lastName() {
            return lastName;
        }
    }

    private String normalizeRequired(String value, String errorMessage) {
        String normalized = normalizeOptional(value);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(errorMessage);
        }
        return normalized;
    }

    private long parseLongOrZero(String value) {
        String normalized = normalizeOptional(value);
        if (normalized.isEmpty()) {
            return 0;
        }
        try {
            return Long.parseLong(normalized);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private long parseRequiredLong(String value, String errorMessage) {
        String normalized = normalizeOptional(value);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(errorMessage);
        }
        try {
            return Long.parseLong(normalized);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    private PatientAnamnesis parseAnamnesis(HttpServletRequest request) {
        PatientAnamnesis a = new PatientAnamnesis();
        a.setAssessmentDate(parseDateOrToday(request.getParameter("assessmentDate")));

        a.setChiefComplaint(normalizeOptional(request.getParameter("chiefComplaint")));
        a.setPainLocation(normalizeOptional(request.getParameter("painLocation")));
        a.setPainQuality(normalizeOptional(request.getParameter("painQuality")));
        a.setAssociatedSymptoms(normalizeOptional(request.getParameter("associatedSymptoms")));

        a.setOnsetType(normalizeEnum(request.getParameter("onsetType")));
        a.setOnsetContext(normalizeOptional(request.getParameter("onsetContext")));
        a.setDisabling(parseBoolean(request.getParameter("isDisabling")));
        a.setPainFrequency(normalizeEnum(request.getParameter("painFrequency")));
        a.setPainProgression(normalizeEnum(request.getParameter("painProgression")));
        a.setPainWithMovement(normalizeEnum(request.getParameter("painWithMovement")));
        a.setPainWithRest(normalizeEnum(request.getParameter("painWithRest")));
        a.setNightPain(parseBoolean(request.getParameter("nightPain")));
        a.setMorningPain(parseBoolean(request.getParameter("morningPain")));
        a.setPainIntensity(parseInteger(request.getParameter("painIntensity"), 0, 10));
        a.setUsesPainMeds(parseBoolean(request.getParameter("usesPainMeds")));
        a.setPainMedsEffect(normalizeEnum(request.getParameter("painMedsEffect")));

        a.setClinicalTests(normalizeOptional(request.getParameter("clinicalTests")));
        a.setSpecialistVisits(normalizeOptional(request.getParameter("specialistVisits")));
        a.setPreviousTreatments(normalizeOptional(request.getParameter("previousTreatments")));
        a.setPathologyHistory(normalizeOptional(request.getParameter("pathologyHistory")));
        a.setCurrentRegularDrugs(normalizeOptional(request.getParameter("currentRegularDrugs")));
        a.setSurgeryHistory(normalizeOptional(request.getParameter("surgeryHistory")));
        a.setTraumaHistory(normalizeOptional(request.getParameter("traumaHistory")));
        a.setDevicesHistory(normalizeOptional(request.getParameter("devicesHistory")));
        a.setChewingDisorders(parseBoolean(request.getParameter("chewingDisorders")));
        a.setMajorInfectionsHistory(normalizeOptional(request.getParameter("majorInfectionsHistory")));
        a.setFamilyHistory(normalizeOptional(request.getParameter("familyHistory")));

        a.setHeightCm(parseDecimal(request.getParameter("heightCm")));
        a.setWeightKg(parseDecimal(request.getParameter("weightKg")));
        a.setLifestyle(normalizeEnum(request.getParameter("lifestyle")));
        a.setSportPractice(normalizeOptional(request.getParameter("sportPractice")));
        a.setSubstanceUse(normalizeOptional(request.getParameter("substanceUse")));
        a.setSleepQuality(parseInteger(request.getParameter("sleepQuality"), 0, 4));
        a.setStressLevel(parseInteger(request.getParameter("stressLevel"), 0, 4));
        a.setDietQuality(normalizeEnum(request.getParameter("dietQuality")));
        a.setFemaleCycleNotes(normalizeOptional(request.getParameter("femaleCycleNotes")));
        a.setFreeNotesJson(normalizeFreeNotesJson(request.getParameter("freeNotesJson")));

        return a;
    }

    private List<PatientCondition> parseConditions(HttpServletRequest request) {
        List<PatientCondition> conditions = new ArrayList<>();
        appendConditions(conditions, request.getParameter("conditionsPathology"), ConditionCategory.PATHOLOGY);
        appendConditions(conditions, request.getParameter("conditionsSymptom"), ConditionCategory.SYMPTOM);
        appendConditions(conditions, request.getParameter("conditionsFamilyHistory"), ConditionCategory.FAMILY_HISTORY);
        appendConditions(conditions, request.getParameter("conditionsAllergy"), ConditionCategory.ALLERGY);
        appendConditions(conditions, request.getParameter("conditionsDrug"), ConditionCategory.DRUG);
        appendConditions(conditions, request.getParameter("conditionsSystemReview"), ConditionCategory.SYSTEM_REVIEW);
        appendConditions(conditions, request.getParameter("conditionsOther"), ConditionCategory.OTHER);
        return conditions;
    }

    private void appendConditions(List<PatientCondition> target, String rawValue, ConditionCategory category) {
        String normalized = normalizeOptional(rawValue);
        if (normalized.isEmpty()) {
            return;
        }

        String[] parts = normalized.split("[,;\\n]+");
        for (String part : parts) {
            String label = part == null ? "" : part.trim();
            if (label.isEmpty()) {
                continue;
            }

            PatientCondition condition = new PatientCondition();
            condition.setCategory(category);
            condition.setLabel(label);
            condition.setStatus("PRESENT");
            target.add(condition);
        }
    }

    private boolean hasAnamnesisData(PatientAnamnesis a, List<PatientCondition> conditions) {
        return !normalizeOptional(a.getChiefComplaint()).isEmpty()
                || !normalizeOptional(a.getPainLocation()).isEmpty()
                || !normalizeOptional(a.getPainQuality()).isEmpty()
                || !normalizeOptional(a.getAssociatedSymptoms()).isEmpty()
                || !normalizeOptional(a.getOnsetType()).isEmpty()
                || !normalizeOptional(a.getOnsetContext()).isEmpty()
                || a.getDisabling() != null
                || !normalizeOptional(a.getPainFrequency()).isEmpty()
                || !normalizeOptional(a.getPainProgression()).isEmpty()
                || !normalizeOptional(a.getPainWithMovement()).isEmpty()
                || !normalizeOptional(a.getPainWithRest()).isEmpty()
                || a.getNightPain() != null
                || a.getMorningPain() != null
                || a.getPainIntensity() != null
                || a.getUsesPainMeds() != null
                || !normalizeOptional(a.getPainMedsEffect()).isEmpty()
                || !normalizeOptional(a.getClinicalTests()).isEmpty()
                || !normalizeOptional(a.getSpecialistVisits()).isEmpty()
                || !normalizeOptional(a.getPreviousTreatments()).isEmpty()
                || !normalizeOptional(a.getPathologyHistory()).isEmpty()
                || !normalizeOptional(a.getCurrentRegularDrugs()).isEmpty()
                || !normalizeOptional(a.getSurgeryHistory()).isEmpty()
                || !normalizeOptional(a.getTraumaHistory()).isEmpty()
                || !normalizeOptional(a.getDevicesHistory()).isEmpty()
                || a.getChewingDisorders() != null
                || !normalizeOptional(a.getMajorInfectionsHistory()).isEmpty()
                || !normalizeOptional(a.getFamilyHistory()).isEmpty()
                || a.getHeightCm() != null
                || a.getWeightKg() != null
                || !normalizeOptional(a.getLifestyle()).isEmpty()
                || !normalizeOptional(a.getSportPractice()).isEmpty()
                || !normalizeOptional(a.getSubstanceUse()).isEmpty()
                || a.getSleepQuality() != null
                || a.getStressLevel() != null
                || !normalizeOptional(a.getDietQuality()).isEmpty()
                || !normalizeOptional(a.getFemaleCycleNotes()).isEmpty()
                || !normalizeOptional(a.getFreeNotesJson()).isEmpty()
                || (conditions != null && !conditions.isEmpty());
    }

    private LocalDate parseDateOrToday(String value) {
        String normalized = normalizeOptional(value);
        if (normalized.isEmpty()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(normalized);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Data anamnesi non valida");
        }
    }

    private YearMonth parseYearMonth(String value) {
        try {
            return YearMonth.parse(normalizeOptional(value));
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Formato mese non valido (atteso YYYY-MM)");
        }
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(normalizeOptional(value));
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Formato data non valido (atteso YYYY-MM-DD)");
        }
    }

    private Boolean parseBoolean(String value) {
        String normalized = normalizeOptional(value).toLowerCase();
        if (normalized.isEmpty()) {
            return null;
        }
        if ("si".equals(normalized) || "yes".equals(normalized) || "true".equals(normalized) || "1".equals(normalized)) {
            return true;
        }
        if ("no".equals(normalized) || "false".equals(normalized) || "0".equals(normalized)) {
            return false;
        }
        throw new IllegalArgumentException("Valore booleano non valido: " + value);
    }

    private Integer parseInteger(String value, int min, int max) {
        String normalized = normalizeOptional(value);
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(normalized);
            if (parsed < min || parsed > max) {
                throw new IllegalArgumentException("Valore fuori intervallo: " + value);
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Numero non valido: " + value);
        }
    }

    private BigDecimal parseDecimal(String value) {
        String normalized = normalizeOptional(value);
        if (normalized.isEmpty()) {
            return null;
        }
        normalized = normalized.replace(",", ".");
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Numero decimale non valido: " + value);
        }
    }

    private String normalizeEnum(String value) {
        String normalized = normalizeOptional(value);
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.toUpperCase();
    }

    private String normalizeFreeNotesJson(String value) {
        String normalized = normalizeOptional(value);
        if (normalized.isEmpty()) {
            return null;
        }

        if (isValidJson(normalized)) {
            return normalized;
        }

        ObjectNode wrapper = OBJECT_MAPPER.createObjectNode();
        wrapper.put("note", normalized);
        return wrapper.toString();
    }

    private boolean isValidJson(String value) {
        try {
            OBJECT_MAPPER.readTree(value);
            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }
}
