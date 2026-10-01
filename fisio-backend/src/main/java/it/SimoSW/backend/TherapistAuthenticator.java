package it.SimoSW.backend;

import com.sun.net.httpserver.HttpExchange;
import it.SimoSW.controller.application.AuthenticationController;
import it.SimoSW.exception.AuthenticationFailedException;
import it.SimoSW.model.User;
import it.SimoSW.model.UserRole;
import it.SimoSW.model.dao.UserDAO;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

final class TherapistAuthenticator {
    private final AuthenticationController authentication;
    private final UserDAO users;

    TherapistAuthenticator(AuthenticationController authentication, UserDAO users) {
        this.authentication = authentication;
        this.users = users;
    }

    User authenticate(HttpExchange exchange) {
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (authorization == null || !authorization.startsWith("Basic ")) {
            return null;
        }
        try {
            String pair = new String(Base64.getDecoder().decode(authorization.substring(6)), StandardCharsets.UTF_8);
            int separator = pair.indexOf(':');
            if (separator <= 0) {
                return null;
            }
            User user = authentication.authenticate(pair.substring(0, separator), pair.substring(separator + 1));
            return user.getRole() == UserRole.THERAPIST ? user : null;
        } catch (IllegalArgumentException | AuthenticationFailedException exception) {
            return null;
        }
    }

    Long therapistId(User user) {
        return users.findIdByUsernameAndRole(user.getUsername(), UserRole.THERAPIST).orElse(null);
    }
}
