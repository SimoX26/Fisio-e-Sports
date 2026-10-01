package it.SimoSW.controller.graphic;

import it.SimoSW.util.bootstrap.ApplicationInitializer;
import it.SimoSW.controller.application.CalendarController;
import it.SimoSW.controller.application.TreatmentController;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

@WebServlet("/treatment-history")
public class TreatmentHistoryServlet extends HttpServlet {

    private TreatmentController treatmentController;
    private CalendarController calendarController;

    @Override
    public void init() {
        ApplicationInitializer initializer =
                (ApplicationInitializer) getServletContext()
                        .getAttribute("appInitializer");

        this.treatmentController = initializer.getTreatmentController();
        this.calendarController = initializer.getCalendarController();
    }

    /* =========================
       GET → cronologia sedute avviate
             (solo piani multi-trattamento)
       ========================= */
    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response)
            throws ServletException, IOException {

        String loggedUser = (String) request.getSession().getAttribute("loggedUser");
        if (loggedUser == null || loggedUser.isBlank()) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        long therapistId = calendarController.resolveTherapistUserIdFromUsername(loggedUser);
        String patientIdParam = request.getParameter("patientId");

        List<TreatmentController.TreatmentHistoryEntry> sessions;
        String historyTitle;
        String historySubtitle;

        if (patientIdParam != null && !patientIdParam.isBlank()) {
            try {
                long patientId = Long.parseLong(patientIdParam);
                sessions = treatmentController.getTreatmentChronologyForPatient(therapistId, patientId);
                historyTitle = "Cronologia trattamenti paziente";
                historySubtitle = "Cronologia completa sessioni (singole e multi-trattamento)";
            } catch (NumberFormatException ex) {
                sessions = treatmentController.getStartedHistoryForTherapistWithMultiSessionPlans(therapistId);
                historyTitle = "Storico trattamenti";
                historySubtitle = "Parametro patientId non valido: visualizzo lo storico generale";
            }
        } else {
            sessions = treatmentController.getStartedHistoryForTherapistWithMultiSessionPlans(therapistId);
            historyTitle = "Storico trattamenti";
            historySubtitle = "Cronologia delle sessioni avviate (singole e multi-trattamento)";
        }

        request.setAttribute("sessions", sessions);
        request.setAttribute("historyTitle", historyTitle);
        request.setAttribute("historySubtitle", historySubtitle);

        request.getRequestDispatcher("/WEB-INF/jsp/therapist/treatmenthistory.jsp")
                .forward(request, response);
    }
}
