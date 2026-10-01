package it.SimoSW.controller.graphic;

import it.SimoSW.controller.application.CalendarController;
import it.SimoSW.controller.application.WaitlistController;
import it.SimoSW.model.Appointment;
import it.SimoSW.model.AppointmentState;
import it.SimoSW.model.WaitlistEntry;
import it.SimoSW.service.whatsapp.WhatsAppConfigurationService;
import it.SimoSW.util.bootstrap.ApplicationInitializer;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@WebServlet("/dashboard")
public class DashboardServlet extends HttpServlet {

    private static final ZoneId HOME_ZONE_ID = ZoneId.of("Europe/Rome");
    private CalendarController calendarController;
    private WaitlistController waitlistController;
    private WhatsAppConfigurationService whatsAppConfigurationService;

    public static class AgendaItemView {
        private final String startTime;
        private final String endTime;
        private final String primaryText;
        private final String eventTitle;
        private final String patientName;
        private final String stateClass;
        private final String stateLabel;

        public AgendaItemView(String startTime, String endTime, String primaryText, String eventTitle, String patientName, String stateClass, String stateLabel) {
            this.startTime = startTime;
            this.endTime = endTime;
            this.primaryText = primaryText;
            this.eventTitle = eventTitle;
            this.patientName = patientName;
            this.stateClass = stateClass;
            this.stateLabel = stateLabel;
        }

        public String getStartTime() { return startTime; }
        public String getEndTime() { return endTime; }
        public String getPrimaryText() { return primaryText; }
        public String getEventTitle() { return eventTitle; }
        public String getPatientName() { return patientName; }
        public String getStateClass() { return stateClass; }
        public String getStateLabel() { return stateLabel; }
    }

    @Override
    public void init() {
        ApplicationInitializer initializer = (ApplicationInitializer) getServletContext().getAttribute("appInitializer");
        this.calendarController = initializer.getCalendarController();
        this.waitlistController = initializer.getWaitlistController();
        this.whatsAppConfigurationService = initializer.getWhatsAppConfigurationService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        int appointmentsToday = 0;
        int patientsToday = 0;
        int completedToday = 0;
        int remindersToSendToday = 0;
        long bookedHoursToday = 0;
        int patientsThisMonth = 0;
        long bookedHoursThisWeek = 0;
        List<WaitlistEntry> waitlistEntries = new ArrayList<>();
        LocalDate today = LocalDate.now(HOME_ZONE_ID);
        LocalTime now = LocalTime.now(HOME_ZONE_ID);
        LocalDateTime nowDateTime = LocalDateTime.now(HOME_ZONE_ID);
        String todayLabel = formatFullDateLabel(today);
        String patientsTodayParam = today.toString();
        String patientsMonthYearLabel = formatMonthYearLabel(today);
        String patientsMonthParam = YearMonth.from(today).toString();
        String weekRangeLabel = formatWeekRangeLabel(today);
        String loggedUserDisplay = "";
        String greetingPrefix = now.getHour() > 15 ? "Buonasera" : "Buongiorno";

        try {
            String loggedUser = (String) request.getSession().getAttribute("loggedUser");
            if (loggedUser != null && !loggedUser.isBlank()) {
                loggedUserDisplay = toDisplayName(loggedUser);
                long therapistId = calendarController.resolveTherapistUserIdFromUsername(loggedUser);
                request.setAttribute("whatsAppConfigured", whatsAppConfigurationService.hasConfiguration(therapistId));

                LocalDateTime todayStart = today.atStartOfDay();
                LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();
                List<Appointment> todayAppointments = calendarController
                        .getAppointmentsForTherapistInPeriod(therapistId, todayStart, tomorrowStart)
                        ;
                waitlistEntries = waitlistController.getEntriesForTherapist(therapistId);
                appointmentsToday = todayAppointments.size();
                List<AgendaItemView> todayAgenda = todayAppointments.stream()
                        .sorted((a, b) -> {
                            LocalDateTime sa = a.getStart();
                            LocalDateTime sb = b.getStart();
                            if (sa == null && sb == null) return 0;
                            if (sa == null) return 1;
                            if (sb == null) return -1;
                            return sa.compareTo(sb);
                        })
                        .map(a -> new AgendaItemView(
                                formatTime(a.getStart()),
                                formatTime(a.getEnd()),
                                resolveAgendaPrimaryText(a.getPatientId(), a.getTitle(), therapistId),
                                resolveEventTitle(a.getTitle()),
                                resolvePatientDisplayName(a.getPatientId(), therapistId),
                                a.getState() == null ? "SCHEDULED" : a.getState().name(),
                                toItalianStateLabel(a.getState())
                        ))
                        .collect(Collectors.toList());
                request.setAttribute("todayAgenda", todayAgenda);

                Set<Long> patientIdsToday = new HashSet<>();
                for (Appointment appointment : todayAppointments) {
                    if (appointment.getPatientId() != null && appointment.getState() != AppointmentState.CANCELLED) {
                        patientIdsToday.add(appointment.getPatientId());
                    }
                    if (appointment.getState() == AppointmentState.COMPLETED) {
                        completedToday++;
                    }
                    if (appointment.getState() != AppointmentState.CANCELLED
                            && appointment.getPatientId() != null
                            && appointment.getStart() != null
                            && appointment.getStart().isAfter(nowDateTime)) {
                        remindersToSendToday++;
                    }
                    if (appointment.getState() != AppointmentState.CANCELLED
                            && appointment.getPatientId() != null
                            && !appointment.isAllDay()
                            && appointment.getStart() != null
                            && appointment.getEnd() != null) {
                        bookedHoursToday += ChronoUnit.HOURS.between(appointment.getStart(), appointment.getEnd());
                    }
                }
                patientsToday = patientIdsToday.size();

                LocalDate weekStartDate = today.with(DayOfWeek.MONDAY);
                LocalDateTime weekStart = weekStartDate.atStartOfDay();
                LocalDateTime weekEnd = weekStart.plusDays(7);
                List<Appointment> weekAppointments = calendarController
                        .getAppointmentsForTherapistInPeriod(therapistId, weekStart, weekEnd);
                bookedHoursThisWeek = weekAppointments.stream()
                        .filter(a -> a.getState() != AppointmentState.CANCELLED)
                        .filter(a -> a.getPatientId() != null)
                        .filter(a -> !a.isAllDay())
                        .mapToLong(a -> ChronoUnit.HOURS.between(a.getStart(), a.getEnd()))
                        .sum();

                LocalDate monthStartDate = today.withDayOfMonth(1);
                LocalDateTime monthStart = monthStartDate.atStartOfDay();
                LocalDateTime monthEnd = monthStart.plusMonths(1);
                List<Appointment> monthAppointments = calendarController
                        .getAppointmentsForTherapistInPeriod(therapistId, monthStart, monthEnd);
                Set<Long> patientIds = new HashSet<>();
                for (Appointment appointment : monthAppointments) {
                    if (appointment.getPatientId() != null) {
                        patientIds.add(appointment.getPatientId());
                    }
                }
                patientsThisMonth = patientIds.size();
            }
        } catch (RuntimeException ex) {
            request.setAttribute("error", "Impossibile caricare i dati panoramici in questo momento.");
            request.setAttribute("todayAgenda", new ArrayList<AgendaItemView>());
        }

        request.setAttribute("appointmentsToday", appointmentsToday);
        request.setAttribute("patientsToday", patientsToday);
        request.setAttribute("completedToday", completedToday);
        request.setAttribute("remindersToSendToday", remindersToSendToday);
        request.setAttribute("bookedHoursToday", bookedHoursToday);
        request.setAttribute("todayLabel", todayLabel);
        request.setAttribute("patientsTodayParam", patientsTodayParam);
        request.setAttribute("patientsThisMonth", patientsThisMonth);
        request.setAttribute("bookedHoursThisWeek", bookedHoursThisWeek);
        request.setAttribute("patientsMonthYearLabel", patientsMonthYearLabel);
        request.setAttribute("patientsMonthParam", patientsMonthParam);
        request.setAttribute("weekRangeLabel", weekRangeLabel);
        request.setAttribute("greetingPrefix", greetingPrefix);
        request.setAttribute("loggedUserDisplay", loggedUserDisplay);
        request.setAttribute("waitlistEntries", waitlistEntries);
        request.getRequestDispatcher("/WEB-INF/jsp/therapist/dashboard.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String action = request.getParameter("action");

        try {
            if ("add-waitlist-entry".equals(action)) {
                addWaitlistEntry(request);
                response.sendRedirect(request.getContextPath() + "/dashboard?waitlistCreated=1");
                return;
            }
            if ("remove-waitlist-entry".equals(action)) {
                removeWaitlistEntry(request);
                response.sendRedirect(request.getContextPath() + "/dashboard?waitlistRemoved=1");
                return;
            }

            throw new IllegalArgumentException("Azione dashboard non valida");
        } catch (RuntimeException ex) {
            request.setAttribute("waitlistError", ex.getMessage());
            doGet(request, response);
        }
    }

    private String formatTime(LocalDateTime value) {
        if (value == null) {
            return "--:--";
        }
        String raw = value.toLocalTime().toString();
        return raw.length() >= 5 ? raw.substring(0, 5) : raw;
    }

    private String formatFullDateLabel(LocalDate date) {
        String month = date.getMonth().getDisplayName(TextStyle.FULL, Locale.ITALIAN);
        return date.getDayOfMonth() + " " + month + " " + date.getYear();
    }

    private String formatMonthYearLabel(LocalDate date) {
        String month = date.getMonth().getDisplayName(TextStyle.FULL, Locale.ITALIAN);
        return month + " " + date.getYear();
    }

    private String formatWeekRangeLabel(LocalDate date) {
        LocalDate weekStart = date.with(DayOfWeek.MONDAY);
        LocalDate weekEnd = weekStart.plusDays(6);

        String startMonth = weekStart.getMonth().getDisplayName(TextStyle.FULL, Locale.ITALIAN);
        String endMonth = weekEnd.getMonth().getDisplayName(TextStyle.FULL, Locale.ITALIAN);

        if (weekStart.getMonth() == weekEnd.getMonth()) {
            return weekStart.getDayOfMonth() + " - " + weekEnd.getDayOfMonth() + " " + startMonth;
        }

        return weekStart.getDayOfMonth() + " " + startMonth + " - " + weekEnd.getDayOfMonth() + " " + endMonth;
    }

    private String toDisplayName(String rawUsername) {
        String normalized = rawUsername == null ? "" : rawUsername.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return "";
        }
        return normalized.substring(0, 1).toUpperCase(Locale.ROOT) + normalized.substring(1);
    }

    private String toItalianStateLabel(AppointmentState state) {
        if (state == null) {
            return "IN ATTESA";
        }
        switch (state) {
            case COMPLETED:
                return "COMPLETATO";
            case CANCELLED:
                return "CANCELLATO";
            case SCHEDULED:
                return "PROGRAMMATO";
            default:
                return "IN ATTESA";
        }
    }

    private String resolvePatientDisplayName(Long patientId, long therapistId) {
        if (patientId == null) {
            return "";
        }
        String fullName = calendarController.resolvePatientFullName(patientId, therapistId);
        if (fullName == null || fullName.isBlank()) {
            return "";
        }
        return fullName;
    }

    private String resolveEventTitle(String rawTitle) {
        if (rawTitle == null || rawTitle.isBlank()) {
            return "Evento";
        }
        return rawTitle.trim();
    }

    private String resolveAgendaPrimaryText(Long patientId, String rawTitle, long therapistId) {
        String patientName = resolvePatientDisplayName(patientId, therapistId);
        if (!patientName.isBlank()) {
            return patientName + " - Paziente";
        }
        return resolveEventTitle(rawTitle) + " - Evento";
    }

    private void addWaitlistEntry(HttpServletRequest request) {
        String loggedUser = (String) request.getSession().getAttribute("loggedUser");
        long therapistId = waitlistController.resolveTherapistIdByUsername(loggedUser);

        WaitlistEntry entry = new WaitlistEntry();
        entry.setTherapistId(therapistId);
        entry.setFullName(request.getParameter("patientName"));
        entry.setPhone(request.getParameter("phone"));

        waitlistController.addEntry(entry);
    }

    private void removeWaitlistEntry(HttpServletRequest request) {
        String loggedUser = (String) request.getSession().getAttribute("loggedUser");
        long therapistId = waitlistController.resolveTherapistIdByUsername(loggedUser);
        long entryId = Long.parseLong(request.getParameter("id"));
        waitlistController.removeEntry(entryId, therapistId);
    }
}
