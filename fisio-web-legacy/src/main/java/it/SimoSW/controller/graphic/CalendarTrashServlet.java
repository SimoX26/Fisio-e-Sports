package it.SimoSW.controller.graphic;

import it.SimoSW.controller.application.CalendarController;
import it.SimoSW.util.bootstrap.ApplicationInitializer;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@WebServlet("/calendar/trash")
public class CalendarTrashServlet extends HttpServlet {

    private CalendarController calendarController;

    @Override
    public void init() {
        ApplicationInitializer initializer = (ApplicationInitializer) getServletContext().getAttribute("appInitializer");
        this.calendarController = initializer.getCalendarController();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String loggedUser = (String) request.getSession().getAttribute("loggedUser");
        if (loggedUser == null || loggedUser.isBlank()) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        long therapistId = calendarController.resolveTherapistUserIdFromUsername(loggedUser);
        calendarController.purgeExpiredTrashForTherapist(therapistId);
        List<CalendarController.CancelledAppointmentView> cancelledAppointments =
                calendarController.getCancelledAppointmentsForTherapist(therapistId);
        String patientSort = normalizeOptional(request.getParameter("sortPatient")).toLowerCase(Locale.ROOT);
        if ("asc".equals(patientSort) || "desc".equals(patientSort)) {
            Comparator<CalendarController.CancelledAppointmentView> byPatientName = Comparator.comparing(
                    row -> normalizeOptional(row.getPatientFullName()).toLowerCase(Locale.ROOT)
            );
            if ("desc".equals(patientSort)) {
                byPatientName = byPatientName.reversed();
            }
            cancelledAppointments.sort(byPatientName);
        }

        request.setAttribute("error", request.getParameter("error"));
        request.setAttribute("message", request.getParameter("message"));
        request.setAttribute("patientSort", patientSort);
        request.setAttribute("cancelledAppointments", cancelledAppointments);
        request.getRequestDispatcher("/WEB-INF/jsp/therapist/calendarTrash.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String loggedUser = (String) request.getSession().getAttribute("loggedUser");
        if (loggedUser == null || loggedUser.isBlank()) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        try {
            long therapistId = calendarController.resolveTherapistUserIdFromUsername(loggedUser);
            String action = request.getParameter("action");
            if ("empty-trash".equals(action)) {
                int deletedCount = calendarController.emptyTrashForTherapist(therapistId);
                String message = URLEncoder.encode(
                        "Cestino svuotato. Appuntamenti eliminati: " + deletedCount,
                        StandardCharsets.UTF_8
                );
                response.sendRedirect(request.getContextPath() + "/calendar/trash?message=" + message);
                return;
            }

            long appointmentId = Long.parseLong(request.getParameter("id"));
            if ("restore".equals(action)) {
                calendarController.restoreAppointment(appointmentId, therapistId);
            } else if ("delete".equals(action)) {
                calendarController.deleteCancelledAppointment(appointmentId, therapistId);
            } else {
                throw new IllegalArgumentException("Azione non valida");
            }

            response.sendRedirect(request.getContextPath() + "/calendar/trash");
        } catch (RuntimeException ex) {
            String error = ex.getMessage() == null ? "Operazione non riuscita" : ex.getMessage();
            String encoded = URLEncoder.encode(error, StandardCharsets.UTF_8);
            response.sendRedirect(request.getContextPath() + "/calendar/trash?error=" + encoded);
        }
    }

    private String normalizeOptional(String value) {
        return value == null ? "" : value.trim();
    }
}
