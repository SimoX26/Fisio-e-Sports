package it.SimoSW.controller.graphic;

import it.SimoSW.controller.application.CalendarController;
import it.SimoSW.model.UserRole;
import it.SimoSW.model.dao.database.ConnectionFactory;
import it.SimoSW.util.bootstrap.ApplicationInitializer;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@WebServlet(urlPatterns = {"/search", "/admin/search"})
public class GlobalSearchServlet extends HttpServlet {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private CalendarController calendarController;

    @Override
    public void init() {
        ApplicationInitializer initializer =
                (ApplicationInitializer) getServletContext().getAttribute("appInitializer");
        this.calendarController = initializer.getCalendarController();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String query = normalize(request.getParameter("q"));
        String userRole = String.valueOf(request.getSession().getAttribute("userRole"));
        String loggedUser = String.valueOf(request.getSession().getAttribute("loggedUser"));
        boolean therapist = UserRole.THERAPIST.name().equals(userRole);

        Long therapistId = null;
        if (therapist && loggedUser != null && !loggedUser.isBlank()) {
            therapistId = calendarController.resolveTherapistUserIdFromUsername(loggedUser);
        }

        List<SearchResult> results = new ArrayList<>();
        if (!query.isBlank() && therapistId != null) {
            try (Connection conn = ConnectionFactory.getConnection()) {
                searchPatients(conn, query, therapistId, results);
                searchAppointments(conn, query, therapistId, results);
                searchTreatments(conn, query, therapistId, results);
            } catch (SQLException ex) {
                request.setAttribute("error", "Errore durante la ricerca globale.");
            }
        }

        request.setAttribute("query", query);
        request.setAttribute("results", results);
        request.getRequestDispatcher("/WEB-INF/jsp/searchResults.jsp").forward(request, response);
    }

    private void searchPatients(Connection conn, String query, long therapistId, List<SearchResult> results) throws SQLException {
        String sql = """
                SELECT id, first_name, last_name, email, phone
                FROM patients
                WHERE therapist_id = ? AND (LOWER(first_name) LIKE ?
                   OR LOWER(last_name) LIKE ?
                   OR LOWER(CONCAT(first_name, ' ', last_name)) LIKE ?
                   OR LOWER(COALESCE(email, '')) LIKE ?
                   OR LOWER(COALESCE(phone, '')) LIKE ?)
                ORDER BY last_name, first_name
                LIMIT 15
                """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            String like = "%" + query + "%";
            stmt.setLong(1, therapistId);
            for (int index = 2; index <= 6; index++) {
                stmt.setString(index, like);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    SearchResult r = new SearchResult();
                    r.type = "Paziente";
                    r.title = (rs.getString("first_name") + " " + rs.getString("last_name")).trim();
                    r.subtitle = "Email: " + safe(rs.getString("email")) + " • Tel: " + safe(rs.getString("phone"));
                    r.link = "/address-book";
                    results.add(r);
                }
            }
        }
    }

    private void searchAppointments(Connection conn, String query, Long therapistId, List<SearchResult> results) throws SQLException {
        String baseSql = """
                SELECT a.id, a.start_time, a.end_time, a.state, a.all_day, a.notes,
                       p.id AS patient_id, p.first_name, p.last_name
                FROM appointments a
                INNER JOIN patients p ON p.id = a.patient_id
                WHERE (
                        LOWER(CONCAT(p.first_name, ' ', p.last_name)) LIKE ?
                     OR LOWER(COALESCE(a.notes, '')) LIKE ?
                     OR LOWER(a.state) LIKE ?
                )
                """;
        String endSql = """
                ORDER BY a.start_time DESC
                LIMIT 20
                """;
        String sql = baseSql + " AND a.therapist_id = ? AND p.therapist_id = ? " + endSql;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            String like = "%" + query + "%";
            stmt.setString(1, like);
            stmt.setString(2, like);
            stmt.setString(3, like);
            stmt.setLong(4, therapistId);
            stmt.setLong(5, therapistId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Timestamp start = rs.getTimestamp("start_time");
                    Timestamp end = rs.getTimestamp("end_time");
                    String patient = (rs.getString("first_name") + " " + rs.getString("last_name")).trim();

                    SearchResult r = new SearchResult();
                    r.type = "Appuntamento";
                    r.title = patient;
                    String timing = start == null
                            ? "-"
                            : DATE_TIME_FORMATTER.format(start.toLocalDateTime())
                                    + (end == null ? "" : " - " + DATE_TIME_FORMATTER.format(end.toLocalDateTime()));
                    String allDayLabel = rs.getBoolean("all_day") ? " • Tutto il giorno" : "";
                    r.subtitle = timing + " • Stato: " + translateAppointmentState(rs.getString("state")) + allDayLabel;
                    String dateParam = start == null
                            ? "today"
                            : start.toLocalDateTime().toLocalDate().toString();
                    long appointmentId = rs.getLong("id");
                    long patientId = rs.getLong("patient_id");
                    r.link = "/calendar?view=timeGridDay&date=" + dateParam
                            + "&highlightAppointmentId=" + appointmentId
                            + "&highlightPatientId=" + patientId;
                    results.add(r);
                }
            }
        }
    }

    private void searchTreatments(Connection conn, String query, Long therapistId, List<SearchResult> results) throws SQLException {
        String baseSql = """
                SELECT ts.id, ts.start_time, ts.state, ts.session_outcome, ts.notes, ts.patient_id,
                       tp.title AS plan_title,
                       p.first_name, p.last_name
                FROM treatment_sessions ts
                INNER JOIN treatment_plans tp ON tp.id = ts.treatment_plan_id
                INNER JOIN patients p ON p.id = ts.patient_id
                WHERE (
                        LOWER(CONCAT(p.first_name, ' ', p.last_name)) LIKE ?
                     OR LOWER(COALESCE(tp.title, '')) LIKE ?
                     OR LOWER(COALESCE(ts.session_outcome, '')) LIKE ?
                     OR LOWER(COALESCE(ts.notes, '')) LIKE ?
                     OR LOWER(ts.state) LIKE ?
                )
                """;
        String endSql = """
                ORDER BY ts.start_time DESC
                LIMIT 20
                """;
        String sql = baseSql + " AND ts.therapist_id = ? AND p.therapist_id = ? " + endSql;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            String like = "%" + query + "%";
            stmt.setString(1, like);
            stmt.setString(2, like);
            stmt.setString(3, like);
            stmt.setString(4, like);
            stmt.setString(5, like);
            stmt.setLong(6, therapistId);
            stmt.setLong(7, therapistId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Timestamp start = rs.getTimestamp("start_time");
                    String patient = (rs.getString("first_name") + " " + rs.getString("last_name")).trim();

                    SearchResult r = new SearchResult();
                    r.type = "Trattamento";
                    r.title = safe(rs.getString("plan_title"));
                    String startLabel = start == null ? "-" : DATE_TIME_FORMATTER.format(start.toLocalDateTime());
                    r.subtitle = "Paziente: " + patient + " • " + startLabel + " • Stato: " + translateTreatmentState(rs.getString("state"));
                    r.link = "/treatment-history?patientId=" + rs.getLong("patient_id");
                    results.add(r);
                }
            }
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String safe(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.trim();
    }

    private String translateAppointmentState(String state) {
        if (state == null) {
            return "-";
        }
        return switch (state.toUpperCase(Locale.ROOT)) {
            case "SCHEDULED" -> "Pianificato";
            case "CANCELLED" -> "Annullato";
            case "COMPLETED" -> "Completato";
            default -> state;
        };
    }

    private String translateTreatmentState(String state) {
        if (state == null) {
            return "-";
        }
        return switch (state.toUpperCase(Locale.ROOT)) {
            case "PLANNED" -> "Pianificata";
            case "IN_PROGRESS" -> "In corso";
            case "COMPLETED" -> "Completata";
            case "CANCELLED" -> "Annullata";
            default -> state;
        };
    }

    public static class SearchResult {
        private String type;
        private String title;
        private String subtitle;
        private String link;

        public String getType() {
            return type;
        }

        public String getTitle() {
            return title;
        }

        public String getSubtitle() {
            return subtitle;
        }

        public String getLink() {
            return link;
        }
    }
}
