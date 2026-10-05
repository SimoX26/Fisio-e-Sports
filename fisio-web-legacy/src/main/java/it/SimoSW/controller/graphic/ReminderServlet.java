package it.SimoSW.controller.graphic;

import it.SimoSW.controller.application.CalendarController;
import it.SimoSW.model.Appointment;
import it.SimoSW.model.AppointmentState;
import it.SimoSW.model.dao.ReminderTemplateDAO;
import it.SimoSW.service.whatsapp.WhatsAppBaileysService;
import it.SimoSW.service.whatsapp.WhatsAppConfigurationService;
import it.SimoSW.util.bootstrap.ApplicationInitializer;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@WebServlet("/promemoria")
public class ReminderServlet extends HttpServlet {

    private static final ZoneId ROME_ZONE_ID = ZoneId.of("Europe/Rome");
    private static final DateTimeFormatter DAY_LABEL_FORMATTER = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ITALIAN);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final String DEFAULT_TEMPLATE = "Le ricordiamo l'appuntamento fissato per {giorno} per l'orario {ora inizio - ora fine}.";

    private CalendarController calendarController;
    private WhatsAppConfigurationService whatsAppConfigurationService;
    private WhatsAppBaileysService whatsAppBaileysService;
    private ReminderTemplateDAO reminderTemplateDAO;

    public static class AppointmentOption {
        private final long id;
        private final String label;
        private final String patientName;
        private final String patientPhone;
        private final boolean sendable;

        public AppointmentOption(long id, String label, String patientName, String patientPhone, boolean sendable) {
            this.id = id;
            this.label = label;
            this.patientName = patientName;
            this.patientPhone = patientPhone;
            this.sendable = sendable;
        }

        public long getId() { return id; }
        public String getLabel() { return label; }
        public String getPatientName() { return patientName; }
        public String getPatientPhone() { return patientPhone; }
        public boolean isSendable() { return sendable; }
    }

    @Override
    public void init() {
        ApplicationInitializer initializer = (ApplicationInitializer) getServletContext().getAttribute("appInitializer");
        this.calendarController = initializer.getCalendarController();
        this.whatsAppConfigurationService = initializer.getWhatsAppConfigurationService();
        this.whatsAppBaileysService = initializer.getWhatsAppBaileysService();
        this.reminderTemplateDAO = initializer.getReminderTemplateDAO();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            long therapistId = resolveTherapistIdFromSession(request);
            LocalDate selectedDate = parseOptionalDate(request.getParameter("date"));
            Long selectedAppointmentId = parseOptionalLong(request.getParameter("appointmentId"));
            if (selectedAppointmentId != null) {
                Appointment selectedAppointment = calendarController.getAppointmentForTherapist(selectedAppointmentId, therapistId);
                validateSendableAppointment(selectedAppointment);
                selectedDate = selectedAppointment.getStart().toLocalDate();
            }
            renderPage(request, response, therapistId, selectedDate, selectedAppointmentId, null,
                    "1".equals(request.getParameter("sent")) ? "Promemoria inviato correttamente." : null,
                    null);
        } catch (RuntimeException ex) {
            request.setAttribute("error", ex.getMessage());
            request.getRequestDispatcher("/WEB-INF/jsp/therapist/promemoria.jsp").forward(request, response);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        LocalDate selectedDate = parseOptionalDate(request.getParameter("date"));
        Long selectedAppointmentId = parseOptionalLong(request.getParameter("appointmentId"));
        String template = normalizeText(request.getParameter("template"));

        try {
            long therapistId = resolveTherapistIdFromSession(request);
            if (!whatsAppConfigurationService.hasConfiguration(therapistId)) {
                throw new IllegalStateException("Servizio WhatsApp non configurato per questo account. Contattare l'amministratore di sistema.");
            }
            if (selectedAppointmentId == null) {
                throw new IllegalArgumentException("Seleziona un appuntamento.");
            }

            Appointment appointment = calendarController.getAppointmentForTherapist(selectedAppointmentId, therapistId);
            validateSendableAppointment(appointment);
            String patientName = calendarController.resolvePatientFullName(appointment.getPatientId(), therapistId);
            String patientPhone = normalizeText(calendarController.resolvePatientPhone(appointment.getPatientId(), therapistId));
            if (patientPhone == null) {
                throw new IllegalArgumentException("Il paziente selezionato non ha un numero WhatsApp.");
            }

            String effectiveTemplate = template == null ? resolveTemplate(therapistId) : template;
            reminderTemplateDAO.saveTemplate(therapistId, effectiveTemplate);
            whatsAppBaileysService.sendTextMessage(patientPhone, renderMessage(effectiveTemplate, appointment, patientName));

            response.sendRedirect(request.getContextPath() + "/promemoria?appointmentId=" + appointment.getId() + "&sent=1");
        } catch (RuntimeException ex) {
            long therapistId = resolveTherapistIdFromSession(request);
            renderPage(request, response, therapistId, selectedDate, selectedAppointmentId, ex.getMessage(), null, template);
        }
    }

    private void renderPage(HttpServletRequest request, HttpServletResponse response, long therapistId, LocalDate selectedDate,
                            Long selectedAppointmentId, String error, String success, String templateOverride) throws ServletException, IOException {
        LocalDate effectiveDate = selectedDate == null ? LocalDate.now(ROME_ZONE_ID) : selectedDate;
        List<AppointmentOption> appointments = loadAppointmentOptions(therapistId, effectiveDate);
        AppointmentOption selectedAppointment = appointments.stream()
                .filter(appointment -> selectedAppointmentId != null && appointment.getId() == selectedAppointmentId)
                .findFirst()
                .orElse(null);
        request.setAttribute("selectedDate", effectiveDate.toString());
        request.setAttribute("selectedDateLabel", effectiveDate.format(DAY_LABEL_FORMATTER));
        request.setAttribute("selectedAppointmentId", selectedAppointmentId);
        request.setAttribute("selectedAppointment", selectedAppointment);
        request.setAttribute("appointmentLocked", selectedAppointment != null);
        request.setAttribute("appointments", appointments);
        request.setAttribute("template", templateOverride == null ? resolveTemplate(therapistId) : templateOverride);
        request.setAttribute("whatsAppConfigured", whatsAppConfigurationService.hasConfiguration(therapistId));
        request.setAttribute("error", error);
        request.setAttribute("success", success);
        request.getRequestDispatcher("/WEB-INF/jsp/therapist/promemoria.jsp").forward(request, response);
    }

    private List<AppointmentOption> loadAppointmentOptions(long therapistId, LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();
        List<Appointment> appointments = calendarController.getReminderCandidatesForTherapistInPeriod(therapistId, start, end);
        List<AppointmentOption> options = new ArrayList<>();

        appointments.stream()
                .sorted(Comparator.comparing(Appointment::getStart, Comparator.nullsLast(LocalDateTime::compareTo)))
                .forEach(appointment -> {
                    String patientName = calendarController.resolvePatientFullName(appointment.getPatientId(), therapistId);
                    String patientPhone = normalizeText(calendarController.resolvePatientPhone(appointment.getPatientId(), therapistId));
                    String startTime = formatTime(appointment.getStart());
                    String endTime = formatTime(appointment.getEnd());
                    String phoneLabel = patientPhone == null ? "numero mancante" : patientPhone;
                    options.add(new AppointmentOption(
                            appointment.getId(),
                            startTime + " - " + endTime + " - " + patientName + " - " + phoneLabel,
                            patientName,
                            patientPhone,
                            patientPhone != null
                    ));
                });

        return options;
    }

    private void validateSendableAppointment(Appointment appointment) {
        if (appointment.getState() != AppointmentState.SCHEDULED) {
            throw new IllegalArgumentException("Il promemoria puo essere inviato solo per appuntamenti programmati.");
        }
        if (appointment.getPatientId() == null) {
            throw new IllegalArgumentException("Seleziona un appuntamento collegato a un paziente.");
        }
        if (appointment.getStart() == null || appointment.getEnd() == null) {
            throw new IllegalArgumentException("L'appuntamento selezionato non ha un orario valido.");
        }
    }

    private String renderMessage(String template, Appointment appointment, String patientName) {
        String startTime = formatTime(appointment.getStart());
        String endTime = formatTime(appointment.getEnd());
        String timeRange = startTime + " - " + endTime;
        String dayLabel = appointment.getStart().toLocalDate().format(DAY_LABEL_FORMATTER);
        return template
                .replace("{nome paziente}", patientName == null ? "" : patientName)
                .replace("{giorno}", dayLabel)
                .replace("{ora inizio}", startTime)
                .replace("{ora fine}", endTime)
                .replace("{ora inizio - ora fine}", timeRange);
    }

    private String resolveTemplate(long therapistId) {
        return reminderTemplateDAO.findTemplateByTherapistId(therapistId)
                .map(this::normalizeText)
                .orElse(DEFAULT_TEMPLATE);
    }

    private long resolveTherapistIdFromSession(HttpServletRequest request) {
        String loggedUser = (String) request.getSession().getAttribute("loggedUser");
        if (loggedUser == null || loggedUser.isBlank()) {
            throw new IllegalArgumentException("Sessione non valida: utente non autenticato");
        }
        return calendarController.resolveTherapistUserIdFromUsername(loggedUser);
    }

    private LocalDate parseOptionalDate(String value) {
        if (value == null || value.isBlank()) {
            return LocalDate.now(ROME_ZONE_ID);
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException ex) {
            return LocalDate.now(ROME_ZONE_ID);
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

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String formatTime(LocalDateTime value) {
        return value == null ? "--:--" : value.toLocalTime().format(TIME_FORMATTER);
    }
}
