package it.SimoSW.controller.graphic;

import it.SimoSW.util.bootstrap.ApplicationInitializer;
import it.SimoSW.controller.application.CalendarController;
import it.SimoSW.controller.application.TreatmentController;
import it.SimoSW.exception.TimeSlotNotAvailableException;
import it.SimoSW.model.Appointment;
import it.SimoSW.model.AppointmentState;
import it.SimoSW.model.CalendarEventView;
import it.SimoSW.model.dao.ReminderTemplateDAO;
import it.SimoSW.service.whatsapp.WhatsAppBaileysService;
import it.SimoSW.service.whatsapp.WhatsAppConfigurationService;
import it.SimoSW.util.ApiResponseWriter;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@WebServlet("/calendar")
public class CalendarServlet extends HttpServlet {

    private static final DateTimeFormatter REMINDER_DAY_LABEL_FORMATTER = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ITALIAN);
    private static final DateTimeFormatter REMINDER_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final String DEFAULT_REMINDER_TEMPLATE = "Le ricordiamo l'appuntamento fissato per {giorno} per l'orario {ora inizio - ora fine}.";

    private CalendarController calendarController;
    private TreatmentController treatmentController;
    private WhatsAppConfigurationService whatsAppConfigurationService;
    private WhatsAppBaileysService whatsAppBaileysService;
    private ReminderTemplateDAO reminderTemplateDAO;
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void init() {
        ApplicationInitializer initializer = (ApplicationInitializer) getServletContext().getAttribute("appInitializer");

        this.calendarController = initializer.getCalendarController();
        this.treatmentController = initializer.getTreatmentController();
        this.whatsAppConfigurationService = initializer.getWhatsAppConfigurationService();
        this.whatsAppBaileysService = initializer.getWhatsAppBaileysService();
        this.reminderTemplateDAO = initializer.getReminderTemplateDAO();
    }

    /* =========================
       GET
       ========================= */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        if ("true".equals(request.getParameter("reminderPreview"))) {
            loadReminderPreview(request, response);
            return;
        }

        if ("true".equals(request.getParameter("patients"))) {
            loadPatientSuggestions(request, response);
            return;
        }

        // Caso 1: richiesta eventi
        if ("true".equals(request.getParameter("events"))) {
            loadEvents(request, response);
            return;
        }

        /*
        // Caso 2: richiesta dettagli appuntamento
        if ("true".equals(request.getParameter("details"))) {
            loadAppointmentDetails(request, response);
            return;
        }
         */

        // Caso 3: visualizzazione pagina calendario
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);
        long therapistId = resolveTherapistIdFromSession(request);
        request.setAttribute("whatsAppConfigured", whatsAppConfigurationService.hasConfiguration(therapistId));
        request.getRequestDispatcher("/WEB-INF/jsp/therapist/calendar.jsp").forward(request, response);
    }

    /* =========================
       POST
       ========================= */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        String action = request.getParameter("action");

        try {
            if (action == null || action.isBlank()) {
                throw new IllegalArgumentException("Azione mancante");
            }

            switch (action) {
                case "create" -> createAppointment(request);
                case "reschedule" -> rescheduleAppointment(request);
                case "cancel" -> cancelAppointment(request);
                case "complete" -> completeAppointmentAndCreateTreatment(request);
                case "send-reminders" -> sendReminders(request, response);
                case "save-reminder-template" -> saveReminderTemplate(request, response);
                default -> throw new IllegalArgumentException("Unknown action");
            }

            if (!"send-reminders".equals(action) && !"save-reminder-template".equals(action)) {
                ApiResponseWriter.writeSuccess(
                        response,
                        HttpServletResponse.SC_OK,
                        "Operazione completata",
                        null
                );
            }

        } catch (TimeSlotNotAvailableException ex) {
            ApiResponseWriter.writeError(
                    getServletContext(),
                    response,
                    HttpServletResponse.SC_CONFLICT,
                    "Lo slot selezionato non e disponibile. Scegli un altro orario.",
                    "TIME_SLOT_NOT_AVAILABLE",
                    ex
            );
        } catch (RuntimeException ex) {
            ApiResponseWriter.writeError(
                    getServletContext(),
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    ex.getMessage(),
                    "BAD_REQUEST",
                    ex
            );
        }
    }

    /* =========================
       Support methods
       ========================= */

    private void loadEvents(HttpServletRequest request, HttpServletResponse response) throws IOException {
        long therapistId = resolveTherapistIdFromSession(request);
        LocalDateTime start = parseDateTime(request.getParameter("start"));
        LocalDateTime end = parseDateTime(request.getParameter("end"));

        List<CalendarEventView> appointments = calendarController.getCalendarEventViewsForTherapistInPeriod(therapistId, start, end);
        List<Map<String, Object>> events = new ArrayList<>(appointments.size());

        for (CalendarEventView appointment : appointments) {
            if (appointment.getState() == AppointmentState.CANCELLED) {
                continue;
            }
            Map<String, Object> event = new HashMap<>();
            String patientFullName = appointment.getPatientFullName();
            event.put("id", appointment.getAppointmentId());
            event.put("title", patientFullName);
            event.put("start", appointment.getStart().toString());
            event.put("end", appointment.getEnd().toString());
            event.put("backgroundColor", "#eaf1fb");
            event.put("borderColor", "#7f9fcd");
            event.put("textColor", "#1f2d3d");

            Map<String, Object> extendedProps = new HashMap<>();
            extendedProps.put("patientId", appointment.getPatientId());
            extendedProps.put("patient", patientFullName);
            extendedProps.put("patientPhone", appointment.getPatientPhone());
            extendedProps.put("nonTreatmentEvent", appointment.getPatientId() == null);
            extendedProps.put("therapistId", appointment.getTherapistId());
            extendedProps.put("state", appointment.getState().name());
            extendedProps.put("notes", appointment.getNotes());
            extendedProps.put("allDay", appointment.isAllDay());
            event.put("extendedProps", extendedProps);
            event.put("allDay", appointment.isAllDay());

            events.add(event);
        }

        response.setContentType("application/json; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getWriter(), events);
    }

    private void loadPatientSuggestions(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String query = request.getParameter("q");
        List<String> names = calendarController.searchPatientNames(query, 8, resolveTherapistIdFromSession(request));
        response.setContentType("application/json; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getWriter(), names);
    }

    private void loadReminderPreview(HttpServletRequest request, HttpServletResponse response) throws IOException {
        long therapistId = resolveTherapistIdFromSession(request);
        LocalDate targetDate = parseRequiredDate(request.getParameter("date"));
        String template = normalizeNotes(request.getParameter("template"));
        if (template == null) {
            template = resolveReminderTemplate(therapistId);
        }

        Set<Long> appointmentIds = parseOptionalLongSet(request.getParameterValues("appointmentId"));
        List<Map<String, Object>> recipients = buildReminderRecipients(therapistId, targetDate, template, appointmentIds);
        Map<String, Object> payload = new HashMap<>();
        payload.put("date", targetDate.toString());
        payload.put("dayLabel", formatReminderDayLabel(targetDate));
        payload.put("template", template);
        payload.put("defaultTemplate", DEFAULT_REMINDER_TEMPLATE);
        payload.put("recipients", recipients);

        response.setContentType("application/json; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getWriter(), payload);
    }

    private void saveReminderTemplate(HttpServletRequest request, HttpServletResponse response) throws IOException {
        long therapistId = resolveTherapistIdFromSession(request);
        String template = normalizeNotes(request.getParameter("template"));
        String normalizedTemplate = template == null ? DEFAULT_REMINDER_TEMPLATE : template;
        reminderTemplateDAO.saveTemplate(therapistId, normalizedTemplate);

        Map<String, Object> payload = new HashMap<>();
        payload.put("template", normalizedTemplate);
        payload.put("defaultTemplate", DEFAULT_REMINDER_TEMPLATE);

        ApiResponseWriter.writeSuccess(
                response,
                HttpServletResponse.SC_OK,
                "Messaggio promemoria salvato",
                payload
        );
    }

/*
    private void loadAppointmentDetails(HttpServletRequest request, HttpServletResponse response) throws IOException {

        long appointmentId = Long.parseLong(request.getParameter("id"));

        Appointment appointment = calendarController.getAppointmentDetails(appointmentId);

        response.setContentType("application/json; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getWriter(), appointment);
    }
*/

    private void createAppointment(HttpServletRequest request) {
        String loggedUser = (String) request.getSession().getAttribute("loggedUser");
        if (loggedUser == null || loggedUser.isBlank()) {
            throw new IllegalArgumentException("Sessione non valida: utente non autenticato");
        }

        boolean allDay = parseBooleanParameter(request.getParameter("allDay"));
        boolean nonTreatmentEvent = parseBooleanParameter(request.getParameter("nonTreatmentEvent"));
        String patientName = request.getParameter("patientName");
        String patientPhone = normalizePhone(request.getParameter("patientPhone"));
        long therapistId = calendarController.resolveTherapistUserIdFromUsername(loggedUser);
        Long patientId = null;
        String appointmentTitle = null;
        if (nonTreatmentEvent) {
            appointmentTitle = normalizeRequired(patientName, "Titolo evento obbligatorio");
        } else {
            boolean requireExistingPatient = allDay;
            patientId = requireExistingPatient
                    ? calendarController.resolveExistingPatientId(patientName, therapistId)
                    : calendarController.resolveOrCreatePatientId(patientName, patientPhone, therapistId);
        }

        Appointment a = new Appointment();
        a.setPatientId(patientId);
        a.setTitle(appointmentTitle);
        a.setStart(parseDateTime(request.getParameter("start")));
        a.setEnd(parseDateTime(request.getParameter("end")));
        a.setAllDay(allDay);
        a.setNotes(normalizeNotes(request.getParameter("notes")));
        a.setTherapistId(therapistId);

        calendarController.scheduleAppointment(a);
    }

    private void rescheduleAppointment(HttpServletRequest request) {

        long appointmentId =
                Long.parseLong(request.getParameter("id"));

        String patientName = request.getParameter("patientName");
        LocalDateTime newStart = parseDateTime(request.getParameter("start"));
        LocalDateTime newEnd = parseDateTime(request.getParameter("end"));
        boolean allDay = parseBooleanParameter(request.getParameter("allDay"));
        boolean nonTreatmentEvent = parseBooleanParameter(request.getParameter("nonTreatmentEvent"));
        String notes = normalizeNotes(request.getParameter("notes"));

        calendarController.rescheduleAppointment(
                appointmentId,
                resolveTherapistIdFromSession(request),
                patientName,
                newStart,
                newEnd,
                allDay,
                nonTreatmentEvent,
                notes
        );
    }

    private void cancelAppointment(HttpServletRequest request) {

        long appointmentId = Long.parseLong(request.getParameter("id"));

        calendarController.cancelAppointment(appointmentId, resolveTherapistIdFromSession(request));
    }

    private void sendReminders(HttpServletRequest request, HttpServletResponse response) throws IOException {
        long therapistId = resolveTherapistIdFromSession(request);
        LocalDate targetDate = parseRequiredDate(request.getParameter("date"));
        if (!whatsAppConfigurationService.hasConfiguration(therapistId)) {
            ApiResponseWriter.writeError(
                    getServletContext(),
                    response,
                    428,
                    "Servizio WhatsApp non configurato per questo account. Contattare l'amministratore di sistema.",
                    "WHATSAPP_CONFIG_MISSING",
                    null
            );
            return;
        }

        String template = normalizeNotes(request.getParameter("template"));
        if (template == null) {
            template = resolveReminderTemplate(therapistId);
        }
        reminderTemplateDAO.saveTemplate(therapistId, template);

        Set<Long> appointmentIds = parseOptionalLongSet(request.getParameterValues("appointmentId"));
        List<Map<String, Object>> recipients = buildReminderRecipients(therapistId, targetDate, template, appointmentIds);
        int sentCount = 0;
        int skippedCount = 0;
        int failedCount = 0;
        String firstFailureMessage = null;
        for (Map<String, Object> recipient : recipients) {
            String patientName = String.valueOf(recipient.get("patientName"));
            String patientPhone = valueAsOptional(recipient.get("patientPhone"));
            String message = valueAsOptional(recipient.get("message"));
            if (patientPhone == null) {
                skippedCount++;
                continue;
            }
            try {
                whatsAppBaileysService.sendTextMessage(
                        patientPhone,
                        message
                );
                sentCount++;
            } catch (RuntimeException ex) {
                failedCount++;
                if (firstFailureMessage == null) {
                    firstFailureMessage = "Invio WhatsApp non riuscito per " + patientName + ": " + safeLogMessage(ex.getMessage());
                }
                getServletContext().log("[WHATSAPP_REMINDER_ERROR] patient=" + patientName + " reason=" + safeLogMessage(ex.getMessage()));
            }
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("date", targetDate.toString());
        payload.put("dayLabel", formatReminderDayLabel(targetDate));
        payload.put("processedCount", recipients.size());
        payload.put("sentCount", sentCount);
        payload.put("skippedCount", skippedCount);
        payload.put("failedCount", failedCount);
        payload.put("channel", "BAILEYS");
        if (firstFailureMessage != null) {
            payload.put("firstFailureMessage", firstFailureMessage);
        }

        if (recipients.isEmpty()) {
            ApiResponseWriter.writeSuccess(
                    response,
                    HttpServletResponse.SC_OK,
                    "Nessun promemoria da inviare per la data selezionata",
                    payload
            );
            return;
        }

        if (sentCount == 0 && failedCount == 0 && skippedCount > 0) {
            ApiResponseWriter.writeError(
                    getServletContext(),
                    response,
                    422,
                    "Nessun promemoria inviato: i pazienti del giorno non hanno un numero WhatsApp valido.",
                    "WHATSAPP_RECIPIENTS_INVALID",
                    null
            );
            return;
        }

        if (sentCount == 0 && failedCount > 0) {
            ApiResponseWriter.writeError(
                    getServletContext(),
                    response,
                    HttpServletResponse.SC_BAD_GATEWAY,
                    firstFailureMessage == null ? "Invio promemoria WhatsApp non riuscito." : firstFailureMessage,
                    "WHATSAPP_SEND_FAILED",
                    null
            );
            return;
        }

        ApiResponseWriter.writeSuccess(
                response,
                HttpServletResponse.SC_OK,
                "Promemoria elaborati correttamente",
                payload
        );
    }

    private void completeAppointmentAndCreateTreatment(HttpServletRequest request) {
        long appointmentId = Long.parseLong(request.getParameter("id"));
        Appointment completed = calendarController.completeAppointment(appointmentId, resolveTherapistIdFromSession(request));
        if (completed.getPatientId() == null) {
            throw new IllegalArgumentException("Gli eventi non collegati a trattamento non possono essere completati come trattamento");
        }
        if (!completed.isAllDay()) {
            treatmentController.createTreatmentForCompletedAppointment(
                    appointmentId,
                    normalizeRequired(request.getParameter("planTitle"), "Titolo piano terapeutico obbligatorio"),
                    normalizeNotes(request.getParameter("goals")),
                    parseOptionalInteger(request.getParameter("frequencyPerWeek"), "Frequenza settimanale non valida"),
                    parseOptionalDate(request.getParameter("expectedEndDate"), "Fine prevista non valida"),
                    parseOptionalInteger(request.getParameter("totalSessionsPlanned"), "Numero sedute pianificate non valido"),
                    parseOptionalInteger(request.getParameter("painScorePre"), "Dolore pre non valido"),
                    parseOptionalInteger(request.getParameter("painScorePost"), "Dolore post non valido"),
                    normalizeNotes(request.getParameter("sessionOutcome")),
                    normalizeNotes(request.getParameter("homeExercises")),
                    normalizeNotes(request.getParameter("notes"))
            );
        }
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Date-time mancante");
        }

        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
        }

        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Formato data/ora non valido: " + value);
        }
    }

    private String normalizeNotes(String notes) {
        if (notes == null) {
            return null;
        }

        String normalized = notes.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeRequired(String value, String errorMessage) {
        String normalized = normalizeNotes(value);
        if (normalized == null) {
            throw new IllegalArgumentException(errorMessage);
        }
        return normalized;
    }

    private String normalizePhone(String value) {
        return normalizeNotes(value);
    }

    private Integer parseOptionalInteger(String value, String errorMessage) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    private Long parseOptionalLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Appuntamento non valido.");
        }
    }

    private Set<Long> parseOptionalLongSet(String[] values) {
        Set<Long> result = new HashSet<>();
        if (values == null) {
            return result;
        }
        for (String value : values) {
            Long parsed = parseOptionalLong(value);
            if (parsed != null) {
                result.add(parsed);
            }
        }
        return result;
    }

    private LocalDate parseOptionalDate(String value, String errorMessage) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    private boolean parseBooleanParameter(String value) {
        return value != null && ("true".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value) || "1".equals(value));
    }

    private long resolveTherapistIdFromSession(HttpServletRequest request) {
        String loggedUser = (String) request.getSession().getAttribute("loggedUser");
        if (loggedUser == null || loggedUser.isBlank()) {
            throw new IllegalArgumentException("Sessione non valida: utente non autenticato");
        }
        return calendarController.resolveTherapistUserIdFromUsername(loggedUser);
    }

    private LocalDate parseRequiredDate(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Data promemoria mancante");
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Data promemoria non valida: " + value);
        }
    }

    private List<Map<String, Object>> buildReminderRecipients(long therapistId, LocalDate targetDate, String template) {
        return buildReminderRecipients(therapistId, targetDate, template, Set.of());
    }

    private List<Map<String, Object>> buildReminderRecipients(long therapistId, LocalDate targetDate, String template, Set<Long> appointmentIds) {
        LocalDateTime start = targetDate.atStartOfDay();
        LocalDateTime end = targetDate.plusDays(1).atStartOfDay();
        List<Appointment> appointments = calendarController.getAppointmentsForTherapistInPeriod(therapistId, start, end);
        List<Map<String, Object>> recipients = new ArrayList<>();
        String dayLabel = formatReminderDayLabel(targetDate);

        for (Appointment appointment : appointments) {
            if (appointmentIds != null && !appointmentIds.isEmpty() && !appointmentIds.contains(appointment.getId())) {
                continue;
            }
            if (appointment.getState() != AppointmentState.SCHEDULED) {
                continue;
            }
            if (appointment.getPatientId() == null) {
                continue;
            }
            String patientName = calendarController.resolvePatientFullName(appointment.getPatientId(), therapistId);
            String startTime = appointment.getStart() == null ? "-" : appointment.getStart().toLocalTime().format(REMINDER_TIME_FORMATTER);
            String endTime = appointment.getEnd() == null ? "-" : appointment.getEnd().toLocalTime().format(REMINDER_TIME_FORMATTER);
            String timeRange = startTime + " - " + endTime;
            String renderedMessage = renderReminderMessage(template, patientName, dayLabel, startTime, endTime, timeRange);

            Map<String, Object> row = new HashMap<>();
            row.put("appointmentId", appointment.getId());
            row.put("patientName", patientName);
            row.put("patientEmail", calendarController.resolvePatientEmail(appointment.getPatientId(), therapistId));
            row.put("patientPhone", calendarController.resolvePatientPhone(appointment.getPatientId(), therapistId));
            row.put("dayLabel", dayLabel);
            row.put("startTime", startTime);
            row.put("endTime", endTime);
            row.put("timeRange", timeRange);
            row.put("message", renderedMessage);
            recipients.add(row);
        }
        return recipients;
    }

    private String formatReminderDayLabel(LocalDate date) {
        if (date == null) {
            return "-";
        }
        return date.format(REMINDER_DAY_LABEL_FORMATTER);
    }

    private String renderReminderMessage(String template, String patientName, String dayLabel, String startTime, String endTime, String timeRange) {
        String result = template == null ? DEFAULT_REMINDER_TEMPLATE : template;
        result = result.replace("{nome paziente}", patientName == null ? "" : patientName);
        result = result.replace("{giorno}", dayLabel == null ? "" : dayLabel);
        result = result.replace("{ora inizio}", startTime == null ? "" : startTime);
        result = result.replace("{ora fine}", endTime == null ? "" : endTime);
        result = result.replace("{ora inizio - ora fine}", timeRange == null ? "" : timeRange);
        return result;
    }

    private String valueAsOptional(Object value) {
        if (value == null) {
            return null;
        }
        String raw = String.valueOf(value).trim();
        return raw.isEmpty() || "null".equalsIgnoreCase(raw) ? null : raw;
    }

    private String resolveReminderTemplate(long therapistId) {
        return reminderTemplateDAO.findTemplateByTherapistId(therapistId)
                .map(this::normalizeNotes)
                .orElse(DEFAULT_REMINDER_TEMPLATE);
    }

    private String safeLogMessage(String message) {
        if (message == null || message.isBlank()) {
            return "Errore sconosciuto";
        }
        return message.length() > 180 ? message.substring(0, 180) : message;
    }

}
