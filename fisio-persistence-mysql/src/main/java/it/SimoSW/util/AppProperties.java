package it.SimoSW.util;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class AppProperties {

    private static final String CONFIG_FILE = "config.properties";
    private static final Properties PROPERTIES = loadProperties();

    private AppProperties() {
    }

    public static String get(String key) {
        return PROPERTIES.getProperty(key);
    }

    public static String get(String key, String fallback) {
        String value = PROPERTIES.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    public static int getInt(String key, int fallback) {
        String value = PROPERTIES.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static Properties loadProperties() {
        try {
            Properties props = new Properties();
            String externalFile = System.getenv("FISIO_DB_CONFIG_FILE");
            if (externalFile == null || externalFile.isBlank()) {
                externalFile = System.getProperty("fisio.config.file");
            }
            InputStream input = externalFile == null || externalFile.isBlank()
                    ? AppProperties.class.getClassLoader().getResourceAsStream(CONFIG_FILE)
                    : Files.newInputStream(Path.of(externalFile));
            if (input == null) {
                return props;
            }
            try (input) {
                props.load(new InputStreamReader(input, StandardCharsets.UTF_8));
            }
            return props;
        } catch (Exception e) {
            throw new RuntimeException("Errore lettura configurazione applicativa", e);
        }
    }
}
