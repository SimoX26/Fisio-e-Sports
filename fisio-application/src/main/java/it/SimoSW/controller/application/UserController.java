package it.SimoSW.controller.application;

import it.SimoSW.exception.InvalidUserDataException;
import it.SimoSW.exception.UsernameAlreadyExistsException;
import it.SimoSW.model.User;
import it.SimoSW.model.UserRole;
import it.SimoSW.model.dao.UserDAO;
import it.SimoSW.util.PasswordHasher;

/**
 * Application controller responsible for user management.
 */
public class UserController {

    private final UserDAO userDAO;

    public UserController(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    public void createUser(
            String username,
            String plainPassword,
            String role,
            boolean active
    ) {
        validateUserData(username, plainPassword, role);

        if (userDAO.findByUsername(username).isPresent()) {
            throw new UsernameAlreadyExistsException(username);
        }

        UserRole userRole = UserRole.valueOf(role);
        String passwordHash = PasswordHasher.hashPassword(plainPassword);

        User user = new User(username, passwordHash, userRole, active);

        userDAO.save(user);
    }

    private void validateUserData(String username, String password, String role) {

        if (username == null || username.isBlank()) {
            throw new InvalidUserDataException("Username cannot be null or empty");
        }

        if (password == null || password.length() < 6) {
            throw new InvalidUserDataException("Password must be at least 6 characters long");
        }

        try {
            UserRole.valueOf(role);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidUserDataException("Invalid user role: " + role);
        }
    }
}
