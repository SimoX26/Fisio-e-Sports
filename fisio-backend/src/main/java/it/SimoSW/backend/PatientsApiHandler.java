package it.SimoSW.backend;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import it.SimoSW.controller.application.AddressBookController;
import it.SimoSW.exception.PatientNotFoundException;
import it.SimoSW.model.ConditionCategory;
import it.SimoSW.model.Patient;
import it.SimoSW.model.PatientAnamnesis;
import it.SimoSW.model.PatientCondition;
import it.SimoSW.model.User;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

final class PatientsApiHandler implements HttpHandler {
    private final TherapistAuthenticator authenticator;
    private final AddressBookController addressBook;

    PatientsApiHandler(TherapistAuthenticator authenticator, AddressBookController addressBook) {
        this.authenticator = authenticator;
        this.addressBook = addressBook;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            ApiJson.allowLocalFileOrigin(exchange, "GET, POST, PUT, DELETE, OPTIONS", "Authorization, Content-Type");
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            String method = exchange.getRequestMethod();
            if (!List.of("GET", "POST", "PUT", "DELETE").contains(method)) {
                exchange.getResponseHeaders().set("Allow", "GET, POST, PUT, DELETE");
                ApiJson.send(exchange, 405, "{\"error\":\"method_not_allowed\"}");
                return;
            }
            String path = exchange.getRequestURI().getPath();
            if (!"/api/patients".equals(path) && !path.startsWith("/api/patients/")) {
                ApiJson.send(exchange, 404, "{\"error\":\"not_found\"}");
                return;
            }
            User therapist = authenticator.authenticate(exchange);
            Long therapistId = therapist == null ? null : authenticator.therapistId(therapist);
            if (therapistId == null) {
                ApiJson.send(exchange, 401, "{\"error\":\"unauthorized\"}");
                return;
            }
            if ("POST".equals(method)) {
                if (!"/api/patients".equals(path)) {
                    ApiJson.send(exchange, 404, "{\"error\":\"not_found\"}");
                    return;
                }
                create(exchange, therapistId);
                return;
            }
            if (path.startsWith("/api/patients/")) {
                String suffix = path.substring("/api/patients/".length());
                boolean anamnesisRequest = suffix.endsWith("/anamnesis");
                boolean candidatesRequest = suffix.endsWith("/merge-candidates");
                String rawId = anamnesisRequest ? suffix.substring(0, suffix.length() - "/anamnesis".length())
                        : candidatesRequest ? suffix.substring(0, suffix.length() - "/merge-candidates".length()) : suffix;
                long patientId;
                try {
                    patientId = Long.parseLong(rawId);
                    if (patientId <= 0) throw new NumberFormatException();
                } catch (NumberFormatException exception) {
                    ApiJson.send(exchange, 400, "{\"error\":\"invalid_id\"}");
                    return;
                }
                if (candidatesRequest) {
                    if (!"GET".equals(method)) {
                        ApiJson.send(exchange, 405, "{\"error\":\"method_not_allowed\"}");
                        return;
                    }
                    String fullName = parameter(exchange, "fullName");
                    if (fullName == null || fullName.length() > 201) {
                        ApiJson.send(exchange, 400, "{\"error\":\"invalid_name\"}");
                        return;
                    }
                    String[] name = fullName.trim().split("\\s+", 2);
                    ApiJson.send(exchange, 200, toJson(addressBook.findMergeCandidates(patientId,
                            name[0], name.length > 1 ? name[1] : "", therapistId)));
                } else if (anamnesisRequest) {
                    if (!"GET".equals(method)) {
                        ApiJson.send(exchange, 405, "{\"error\":\"method_not_allowed\"}");
                        return;
                    }
                    PatientAnamnesis anamnesis = addressBook.getLatestAnamnesisByPatientId(patientId, therapistId).orElse(null);
                    List<PatientCondition> conditions = anamnesis == null ? List.of()
                            : addressBook.getConditionsByAnamnesisId(patientId, anamnesis.getId(), therapistId);
                    ApiJson.send(exchange, 200, anamnesisJson(anamnesis, conditions));
                } else if ("PUT".equals(method)) {
                    PatientForm form = PatientForm.read(exchange);
                    Patient patient = form.patient();
                    patient.setId(patientId);
                    addressBook.updatePatientProfile(patient, therapistId);
                    long mergeTargetId = form.mergeTargetId();
                    if (mergeTargetId > 0) {
                        addressBook.mergePatients(patientId, mergeTargetId, therapistId);
                    } else if (form.hasAnamnesisData()) {
                        addressBook.savePatientAnamnesis(patientId, therapist.getUsername(), form.anamnesis(), form.conditions());
                    }
                    exchange.sendResponseHeaders(204, -1);
                } else if ("DELETE".equals(method)) {
                    addressBook.deletePatient(patientId, "1".equals(parameter(exchange, "force")), therapistId);
                    exchange.sendResponseHeaders(204, -1);
                } else {
                    ApiJson.send(exchange, 200, patientJson(addressBook.getPatientById(patientId, therapistId)));
                }
                return;
            }
            if (!"GET".equals(method)) {
                ApiJson.send(exchange, 405, "{\"error\":\"method_not_allowed\"}");
                return;
            }

            String query = parameter(exchange, "q");
            String treatedDate = parameter(exchange, "treatedDate");
            String treatedMonth = parameter(exchange, "treatedMonth");
            String sort = parameter(exchange, "sort");
            if (query != null && query.length() > 200) {
                ApiJson.send(exchange, 400, "{\"error\":\"invalid_query\"}");
                return;
            }
            List<Patient> patients;
            try {
                if (treatedDate != null && !treatedDate.isBlank()) {
                    patients = addressBook.getPatientsTreatedOnDate(therapistId, LocalDate.parse(treatedDate));
                } else if (treatedMonth != null && !treatedMonth.isBlank()) {
                    patients = addressBook.getPatientsTreatedInMonth(therapistId, YearMonth.parse(treatedMonth));
                } else {
                    patients = addressBook.searchPatients(query == null ? "" : query, therapistId);
                }
            } catch (DateTimeParseException exception) {
                ApiJson.send(exchange, 400, "{\"error\":\"invalid_period\"}");
                return;
            }
            List<Patient> sorted = new ArrayList<>(patients);
            Comparator<Patient> byName = Comparator.comparing(patient -> patient.getFullName().toLowerCase(java.util.Locale.ROOT));
            Comparator<Patient> byCreated = Comparator.comparing(Patient::getCreatedAt,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            if (sort == null || sort.isBlank() || "created-desc".equals(sort)) {
                sorted.sort(byCreated.reversed());
            } else if ("created-asc".equals(sort)) {
                sorted.sort(byCreated);
            } else if ("name-asc".equals(sort)) {
                sorted.sort(byName);
            } else if ("name-desc".equals(sort)) {
                sorted.sort(byName.reversed());
            } else {
                ApiJson.send(exchange, 400, "{\"error\":\"invalid_sort\"}");
                return;
            }
            ApiJson.send(exchange, 200, toJson(sorted));
        } catch (PatientNotFoundException exception) {
            ApiJson.send(exchange, 404, "{\"error\":\"not_found\"}");
        } catch (UnsupportedOperationException exception) {
            ApiJson.send(exchange, 415, "{\"error\":\"unsupported_media_type\"}");
        } catch (DateTimeParseException exception) {
            ApiJson.send(exchange, 400, "{\"error\":\"invalid_date\"}");
        } catch (IllegalArgumentException exception) {
            int status = "body_too_large".equals(exception.getMessage()) ? 413 : 400;
            ApiJson.send(exchange, status, "{\"error\":\"invalid_input\"}");
        } catch (RuntimeException exception) {
            System.err.println("Errore API pazienti: " + exception.getClass().getSimpleName());
            ApiJson.send(exchange, 503, "{\"error\":\"unavailable\"}");
        } finally {
            exchange.close();
        }
    }

    private void create(HttpExchange exchange, long therapistId) throws IOException {
        Patient saved = addressBook.registerPatient(PatientForm.read(exchange).patient(), therapistId);
        ApiJson.send(exchange, 201, "{\"id\":" + saved.getId() + "}");
    }

    private static String parameter(HttpExchange exchange, String key) {
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw == null) return null;
        for (String part : raw.split("&")) {
            int separator = part.indexOf('=');
            if (separator > 0 && key.equals(URLDecoder.decode(part.substring(0, separator), StandardCharsets.UTF_8))) {
                return URLDecoder.decode(part.substring(separator + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static String toJson(List<Patient> patients) {
        StringBuilder json = new StringBuilder("[");
        for (Patient patient : patients) {
            if (json.length() > 1) json.append(',');
            json.append(patientJson(patient));
        }
        return json.append(']').toString();
    }

    private static String patientJson(Patient patient) {
        return new StringBuilder("{\"id\":").append(patient.getId())
                    .append(",\"fullName\":").append(ApiJson.quote(patient.getFullName()))
                    .append(",\"firstName\":").append(ApiJson.quote(patient.getFirstName()))
                    .append(",\"lastName\":").append(ApiJson.quote(patient.getLastName()))
                    .append(",\"phone\":").append(ApiJson.quote(patient.getPhone()))
                    .append(",\"email\":").append(ApiJson.quote(patient.getEmail()))
                    .append(",\"createdDateLabel\":").append(ApiJson.quote(patient.getCreatedDateLabel()))
                    .append(",\"state\":").append(ApiJson.quote(patient.getState() == null ? null : patient.getState().name()))
                    .append(",\"linkedAppointmentsCount\":").append(patient.getLinkedAppointmentsCount())
                    .append('}').toString();
    }

    private static String anamnesisJson(PatientAnamnesis a, List<PatientCondition> conditions) {
        if (a == null) return "{}";
        StringBuilder json = new StringBuilder("{");
        field(json, "assessmentDate", a.getAssessmentDate());
        field(json, "chiefComplaint", a.getChiefComplaint());
        field(json, "painLocation", a.getPainLocation());
        field(json, "painQuality", a.getPainQuality());
        field(json, "associatedSymptoms", a.getAssociatedSymptoms());
        field(json, "onsetType", a.getOnsetType());
        field(json, "onsetContext", a.getOnsetContext());
        field(json, "isDisabling", a.getDisabling());
        field(json, "painFrequency", a.getPainFrequency());
        field(json, "painProgression", a.getPainProgression());
        field(json, "painWithMovement", a.getPainWithMovement());
        field(json, "painWithRest", a.getPainWithRest());
        field(json, "nightPain", a.getNightPain());
        field(json, "morningPain", a.getMorningPain());
        field(json, "painIntensity", a.getPainIntensity());
        field(json, "usesPainMeds", a.getUsesPainMeds());
        field(json, "painMedsEffect", a.getPainMedsEffect());
        field(json, "clinicalTests", a.getClinicalTests());
        field(json, "specialistVisits", a.getSpecialistVisits());
        field(json, "previousTreatments", a.getPreviousTreatments());
        field(json, "pathologyHistory", a.getPathologyHistory());
        field(json, "currentRegularDrugs", a.getCurrentRegularDrugs());
        field(json, "surgeryHistory", a.getSurgeryHistory());
        field(json, "traumaHistory", a.getTraumaHistory());
        field(json, "devicesHistory", a.getDevicesHistory());
        field(json, "chewingDisorders", a.getChewingDisorders());
        field(json, "majorInfectionsHistory", a.getMajorInfectionsHistory());
        field(json, "familyHistory", a.getFamilyHistory());
        field(json, "heightCm", a.getHeightCm());
        field(json, "weightKg", a.getWeightKg());
        field(json, "lifestyle", a.getLifestyle());
        field(json, "sportPractice", a.getSportPractice());
        field(json, "substanceUse", a.getSubstanceUse());
        field(json, "sleepQuality", a.getSleepQuality());
        field(json, "stressLevel", a.getStressLevel());
        field(json, "dietQuality", a.getDietQuality());
        field(json, "femaleCycleNotes", a.getFemaleCycleNotes());
        field(json, "freeNotesJson", a.getFreeNotesJson());

        Map<ConditionCategory, StringBuilder> grouped = new EnumMap<>(ConditionCategory.class);
        for (PatientCondition condition : conditions) {
            if (condition.getCategory() == null || condition.getLabel() == null || condition.getLabel().isBlank()) continue;
            StringBuilder labels = grouped.computeIfAbsent(condition.getCategory(), ignored -> new StringBuilder());
            if (labels.length() > 0) labels.append('\n');
            labels.append(condition.getLabel().trim());
        }
        field(json, "conditionsPathology", grouped.get(ConditionCategory.PATHOLOGY));
        field(json, "conditionsSymptom", grouped.get(ConditionCategory.SYMPTOM));
        field(json, "conditionsFamilyHistory", grouped.get(ConditionCategory.FAMILY_HISTORY));
        field(json, "conditionsAllergy", grouped.get(ConditionCategory.ALLERGY));
        field(json, "conditionsDrug", grouped.get(ConditionCategory.DRUG));
        field(json, "conditionsSystemReview", grouped.get(ConditionCategory.SYSTEM_REVIEW));
        field(json, "conditionsOther", grouped.get(ConditionCategory.OTHER));
        return json.append('}').toString();
    }

    private static void field(StringBuilder json, String name, Object value) {
        if (json.length() > 1) json.append(',');
        json.append(ApiJson.quote(name)).append(':')
                .append(ApiJson.quote(value == null ? "" : value.toString()));
    }
}
