package it.SimoSW.desktop;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

final class BackendEndpoint {
    static final String LOCAL_URL = "http://127.0.0.1:8081";

    private BackendEndpoint() {
    }

    static String resolve(String configured) {
        if (configured == null || configured.isBlank()) return LOCAL_URL;
        try {
            URI uri = new URI(configured.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost();
            if (host == null || uri.getUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null || uri.getPort() == 0 || uri.getPort() > 65535)
                throw new IllegalArgumentException();
            String path = uri.getRawPath();
            if (path != null && !path.isEmpty() && !"/".equals(path)) throw new IllegalArgumentException();
            boolean local = "127.0.0.1".equalsIgnoreCase(host) || "localhost".equalsIgnoreCase(host)
                    || "[::1]".equalsIgnoreCase(host);
            if (!"https".equals(scheme) && !(local && "http".equals(scheme))) throw new IllegalArgumentException();
            return scheme + "://" + host.toLowerCase(Locale.ROOT)
                    + (uri.getPort() < 0 ? "" : ":" + uri.getPort());
        } catch (URISyntaxException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("FISIO_BACKEND_URL non valido: usare HTTPS o HTTP solo su localhost", exception);
        }
    }
}
