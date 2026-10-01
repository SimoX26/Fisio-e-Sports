package it.SimoSW.controller.graphic;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.SimoSW.controller.application.CalendarController;
import it.SimoSW.controller.application.KpiSnapshotController;
import it.SimoSW.model.KpiMonthlySnapshot;
import it.SimoSW.util.bootstrap.ApplicationInitializer;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet("/dashboard/kpi")
public class DashboardKpiServlet extends HttpServlet {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private KpiSnapshotController kpiSnapshotController;
    private CalendarController calendarController;

    @Override
    public void init() {
        ApplicationInitializer initializer = (ApplicationInitializer) getServletContext().getAttribute("appInitializer");
        this.kpiSnapshotController = initializer.getKpiSnapshotController();
        this.calendarController = initializer.getCalendarController();
        this.objectMapper.findAndRegisterModules();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String scope = normalizeScope(request.getParameter("scope"));
        int months = parseMonths(request.getParameter("months"));

        Map<String, Object> payload = new HashMap<>();
        payload.put("scope", scope);
        payload.put("months", months);

        try {
            List<KpiMonthlySnapshot> snapshots;
            if ("global".equals(scope)) {
                snapshots = kpiSnapshotController.getRecentGlobalSnapshots(months);
            } else {
                String loggedUser = String.valueOf(request.getSession().getAttribute("loggedUser"));
                if (loggedUser == null || loggedUser.isBlank()) {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Utente non autenticato");
                    return;
                }
                long therapistId = calendarController.resolveTherapistUserIdFromUsername(loggedUser);
                snapshots = kpiSnapshotController.getRecentTherapistSnapshots(therapistId, months);
                payload.put("therapistId", therapistId);
            }

            payload.put("series", snapshots);
            payload.put("count", snapshots.size());
            payload.put("generatedAt", java.time.LocalDateTime.now().toString());

            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("application/json; charset=UTF-8");
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getWriter(), payload);
        } catch (Exception ex) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json; charset=UTF-8");
            response.setCharacterEncoding("UTF-8");
            Map<String, Object> err = new HashMap<>();
            err.put("error", ex.getMessage() == null ? "Errore durante lettura KPI" : ex.getMessage());
            objectMapper.writeValue(response.getWriter(), err);
        }
    }

    private String normalizeScope(String raw) {
        if (raw == null) {
            return "me";
        }
        String normalized = raw.trim().toLowerCase();
        if ("global".equals(normalized)) {
            return "global";
        }
        return "me";
    }

    private int parseMonths(String raw) {
        if (raw == null || raw.isBlank()) {
            return 12;
        }
        try {
            int parsed = Integer.parseInt(raw.trim());
            if (parsed < 1) {
                return 12;
            }
            return Math.min(parsed, 36);
        } catch (NumberFormatException ex) {
            return 12;
        }
    }
}
