package it.SimoSW.model.dao.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

import it.SimoSW.util.AppProperties;

public final class ConnectionFactory {

    private static final HikariDataSource dataSource;

    static {
        try {
            Properties props = new Properties();
            copyProperty(props, "db.driver", "FISIO_DB_DRIVER");
            copyProperty(props, "db.url", "FISIO_DB_URL");
            copyProperty(props, "db.username", "FISIO_DB_USER");
            copyProperty(props, "db.password", "FISIO_DB_PASSWORD");
            copyProperty(props, "db.pool.maxSize");
            copyProperty(props, "db.pool.minIdle");
            copyProperty(props, "db.pool.connectionTimeoutMs");
            copyProperty(props, "db.pool.idleTimeoutMs");
            copyProperty(props, "db.pool.maxLifetimeMs");

            String driver = props.getProperty("db.driver");
            String url = props.getProperty("db.url");
            String username = props.getProperty("db.username");
            String password = props.getProperty("db.password");

            if (driver == null || url == null || username == null || password == null) {
                throw new RuntimeException("Configurazione database incompleta");
            }

            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setDriverClassName(driver);
            hikariConfig.setJdbcUrl(url);
            hikariConfig.setUsername(username);
            hikariConfig.setPassword(password);
            hikariConfig.setPoolName("FisioSportPool");
            hikariConfig.setMaximumPoolSize(readInt(props, "db.pool.maxSize", 5));
            hikariConfig.setMinimumIdle(readInt(props, "db.pool.minIdle", 1));
            hikariConfig.setConnectionTimeout(readLong(props, "db.pool.connectionTimeoutMs", 3000L));
            hikariConfig.setIdleTimeout(readLong(props, "db.pool.idleTimeoutMs", 60000L));
            hikariConfig.setMaxLifetime(readLong(props, "db.pool.maxLifetimeMs", 1800000L));

            dataSource = new HikariDataSource(hikariConfig);

        } catch (Exception e) {
            throw new RuntimeException("Errore inizializzazione ConnectionFactory", e);
        }
    }

    private ConnectionFactory() {
        // Costruttore vuoto che impedisce l'istanziazione
    }

    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    private static void copyProperty(Properties props, String key) {
        copyProperty(props, key, null);
    }

    private static void copyProperty(Properties props, String key, String environmentKey) {
        String value = environmentKey == null ? null : System.getenv(environmentKey);
        if (value == null) {
            value = AppProperties.get(key);
        }
        if ("db.driver".equals(key) && value == null && System.getenv("FISIO_DB_URL") != null) {
            value = "com.mysql.cj.jdbc.Driver";
        }
        if (value != null) {
            props.setProperty(key, value);
        }
    }

    private static int readInt(Properties props, String key, int defaultValue) {
        String raw = props.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private static long readLong(Properties props, String key, long defaultValue) {
        String raw = props.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }
}
