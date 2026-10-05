package it.SimoSW.backend;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import it.SimoSW.controller.application.AuthenticationController;
import it.SimoSW.controller.application.AddressBookController;
import it.SimoSW.controller.application.CalendarController;
import it.SimoSW.controller.application.WaitlistController;
import it.SimoSW.controller.application.TreatmentController;
import it.SimoSW.model.dao.database.DatabaseAppointmentDAO;
import it.SimoSW.model.dao.database.DatabasePatientDAO;
import it.SimoSW.model.dao.database.DatabasePatientAnamnesisDAO;
import it.SimoSW.model.dao.database.DatabasePatientConditionDAO;
import it.SimoSW.model.dao.database.DatabaseRememberMeTokenDAO;
import it.SimoSW.model.dao.database.DatabaseUserDAO;
import it.SimoSW.model.dao.database.DatabaseWaitlistEntryDAO;
import it.SimoSW.model.dao.database.DatabaseTreatmentPlanDAO;
import it.SimoSW.model.dao.database.DatabaseTreatmentSessionDAO;
import it.SimoSW.model.dao.database.DatabaseReminderTemplateDAO;
import it.SimoSW.util.AppProperties;

public final class BackendApplication {

    private BackendApplication() {
    }

    public static void main(String[] args) throws IOException {
        configureFile();
        int port = readPort();
        DriverManager.setLoginTimeout(3);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/health", exchange -> {
            try {
                if (!"GET".equals(exchange.getRequestMethod())) {
                    exchange.getResponseHeaders().set("Allow", "GET");
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }

                sendJson(exchange, 200, "{\"status\":\"ok\"}");
            } finally {
                exchange.close();
            }
        });
        server.createContext("/ready", exchange -> {
            try {
                if (!"GET".equals(exchange.getRequestMethod())) {
                    exchange.getResponseHeaders().set("Allow", "GET");
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }

                if (databaseIsReady()) {
                    sendJson(exchange, 200, "{\"status\":\"ok\"}");
                } else {
                    sendJson(exchange, 503, "{\"status\":\"unavailable\"}");
                }
            } finally {
                exchange.close();
            }
        });
        DatabaseUserDAO users = new DatabaseUserDAO();
        TherapistAuthenticator authenticator = new TherapistAuthenticator(
                new AuthenticationController(users, new DatabaseRememberMeTokenDAO()), users);
        DatabaseAppointmentDAO appointments = new DatabaseAppointmentDAO();
        DatabasePatientDAO patients = new DatabasePatientDAO();
        CalendarController calendar = new CalendarController(appointments, patients, users);
        TreatmentController treatments = new TreatmentController(new DatabaseTreatmentPlanDAO(),
                new DatabaseTreatmentSessionDAO(), patients, appointments);
        CalendarApiHandler calendarApi = new CalendarApiHandler(authenticator, calendar);
        server.createContext("/api/calendar", calendarApi);
        server.createContext("/api/calendar/trash", new CalendarTrashApiHandler(authenticator, calendar));
        DatabaseReminderTemplateDAO reminderTemplates = new DatabaseReminderTemplateDAO();
        server.createContext("/api/reminders/preview", new ReminderPreviewApiHandler(
                authenticator, calendar, reminderTemplates));
        server.createContext("/api/reminders/send", new ReminderSendApiHandler(
                authenticator, calendar, reminderTemplates));
        server.createContext("/api/treatments", new TreatmentsApiHandler(authenticator, calendar, treatments, patients));
        server.createContext("/api/me", calendarApi);
        server.createContext("/api/auth/remember", new RememberApiHandler(authenticator));
        server.createContext("/api/waitlist", new WaitlistApiHandler(
                authenticator, new WaitlistController(new DatabaseWaitlistEntryDAO(), users)));
        server.createContext("/api/patients", new PatientsApiHandler(authenticator,
                new AddressBookController(new DatabasePatientDAO(), new DatabasePatientAnamnesisDAO(),
                        new DatabasePatientConditionDAO(), new DatabaseAppointmentDAO(), users)));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
        server.start();
        System.out.println("Fisio backend in ascolto su http://127.0.0.1:" + port);
    }

    private static void configureFile() {
        if (System.getenv("FISIO_DB_CONFIG_FILE") != null) {
            return;
        }
        try {
            Path besideJar = Path.of(BackendApplication.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).resolveSibling("config.properties");
            Path inWorkspace = Path.of("fisio-backend", "config.properties");
            Path selected = Files.isRegularFile(besideJar) ? besideJar : inWorkspace;
            if (Files.isRegularFile(selected)) {
                System.setProperty("fisio.config.file", selected.toAbsolutePath().toString());
            }
        } catch (URISyntaxException exception) {
            throw new IllegalStateException("Percorso backend non valido", exception);
        }
    }

    private static boolean databaseIsReady() {
        String url = configValue("FISIO_DB_URL", "db.url");
        String user = configValue("FISIO_DB_USER", "db.username");
        String password = configValue("FISIO_DB_PASSWORD", "db.password");
        if (url == null || url.isBlank() || user == null || user.isBlank() || password == null) {
            return false;
        }
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            return connection.isValid(3);
        } catch (SQLException exception) {
            return false;
        }
    }

    private static String configValue(String environmentName, String propertyName) {
        String value = System.getenv(environmentName);
        return value == null ? AppProperties.get(propertyName) : value;
    }

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }

    private static int readPort() {
        String configuredPort = System.getenv("FISIO_BACKEND_PORT");
        if (configuredPort == null || configuredPort.isBlank()) {
            return 8081;
        }
        try {
            int port = Integer.parseInt(configuredPort);
            if (port >= 1 && port <= 65535) {
                return port;
            }
        } catch (NumberFormatException ignored) {
            // Il messaggio seguente include il valore non valido.
        }
        throw new IllegalArgumentException("FISIO_BACKEND_PORT non valido: " + configuredPort);
    }
}
