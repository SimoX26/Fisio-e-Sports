package it.SimoSW.controller.application;

import it.SimoSW.model.dao.UserDAO;
import it.SimoSW.exception.AuthenticationFailedException;
import it.SimoSW.model.User;
import it.SimoSW.model.dao.RememberMeTokenDAO;
import it.SimoSW.util.PasswordHasher;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

public class AuthenticationController {

    private final UserDAO userDAO;
    private final RememberMeTokenDAO rememberMeTokenDAO;
    private static final int REMEMBER_ME_DAYS = 30;
    private static final int TOKEN_BYTES_LENGTH = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public AuthenticationController(UserDAO userDAO, RememberMeTokenDAO rememberMeTokenDAO) {
        this.userDAO = userDAO;
        this.rememberMeTokenDAO = rememberMeTokenDAO;
    }

    public User authenticate(String username, String password) {

        if (username == null || password == null) {
            throw new AuthenticationFailedException("Invalid credentials");
        }

        Optional<User> optionalUser = userDAO.findByUsername(username);

        if (optionalUser.isEmpty()) {
            throw new AuthenticationFailedException("Invalid credentials");
        }

        User user = optionalUser.get();

        if (!PasswordHasher.verifyPassword(password, user.getPasswordHash())) {
            throw new AuthenticationFailedException("Invalid credentials");
        }

        if (!user.isActive()) {
            throw new AuthenticationFailedException("Invalid credentials");
        }

        if (PasswordHasher.isLegacySha256Hash(user.getPasswordHash())) {
            String upgradedHash = PasswordHasher.hashPassword(password);
            userDAO.updatePasswordHashByUsername(user.getUsername(), upgradedHash);
        }

        return user;
    }

    public String createRememberMeToken(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }

        long userId = userDAO.findIdByUsernameAndRole(user.getUsername(), user.getRole())
                .orElseThrow(() -> new AuthenticationFailedException("Unable to resolve user id"));

        String rawToken = generateToken();
        String tokenHash = PasswordHasher.hash(rawToken);
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(REMEMBER_ME_DAYS);

        rememberMeTokenDAO.deleteExpired();
        rememberMeTokenDAO.saveOrUpdate(userId, tokenHash, expiresAt);

        return rawToken;
    }

    public Optional<User> authenticateByRememberMeToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }

        rememberMeTokenDAO.deleteExpired();
        String tokenHash = PasswordHasher.hash(rawToken);

        return rememberMeTokenDAO.findActiveUserByTokenHash(tokenHash);
    }

    public void revokeRememberMeToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }

        String tokenHash = PasswordHasher.hash(rawToken);
        rememberMeTokenDAO.deleteByTokenHash(tokenHash);
    }

    private String generateToken() {
        byte[] tokenBytes = new byte[TOKEN_BYTES_LENGTH];
        SECURE_RANDOM.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }
}
