package it.SimoSW.backend;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class BackendApplication {

    private BackendApplication() {
    }

    public static void main(String[] args) throws IOException {
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
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
        server.start();
        System.out.println("Fisio backend in ascolto su http://127.0.0.1:" + port);
    }

    private static boolean databaseIsReady() {
        String url = System.getenv("FISIO_DB_URL");
        String user = System.getenv("FISIO_DB_USER");
        String password = System.getenv("FISIO_DB_PASSWORD");
        if (url == null || url.isBlank() || user == null || user.isBlank() || password == null) {
            return false;
        }
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            return connection.isValid(3);
        } catch (SQLException exception) {
            return false;
        }
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
