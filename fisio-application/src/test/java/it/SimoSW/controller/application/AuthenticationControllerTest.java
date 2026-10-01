package it.SimoSW.controller.application;

import it.SimoSW.exception.AuthenticationFailedException;
import it.SimoSW.model.User;
import it.SimoSW.model.UserRole;
import it.SimoSW.model.dao.UserDAO;
import it.SimoSW.util.PasswordHasher;
import junit.framework.TestCase;

import java.util.Optional;

public class AuthenticationControllerTest extends TestCase {

    public void testDisabledUserCannotAuthenticateOrUpgradePassword() {
        FakeUserDAO userDAO = new FakeUserDAO(new User(
                "disabled", PasswordHasher.hashSha256("password"), UserRole.THERAPIST, false));
        AuthenticationController controller = new AuthenticationController(userDAO, null);

        try {
            controller.authenticate("disabled", "password");
            fail("Un utente disattivato non deve autenticarsi");
        } catch (AuthenticationFailedException expected) {
            assertEquals("Invalid credentials", expected.getMessage());
        }
        assertFalse(userDAO.passwordUpdated);
    }

    public void testActiveUserStillAuthenticates() {
        User user = new User("active", PasswordHasher.hashSha256("password"), UserRole.THERAPIST, true);
        FakeUserDAO userDAO = new FakeUserDAO(user);
        AuthenticationController controller = new AuthenticationController(userDAO, null);

        assertSame(user, controller.authenticate("active", "password"));
        assertTrue(userDAO.passwordUpdated);
    }

    private static final class FakeUserDAO implements UserDAO {
        private final User user;
        private boolean passwordUpdated;

        private FakeUserDAO(User user) {
            this.user = user;
        }

        @Override
        public Optional<User> findByUsername(String username) {
            return user.getUsername().equals(username) ? Optional.of(user) : Optional.empty();
        }

        @Override
        public void updatePasswordHashByUsername(String username, String passwordHash) {
            passwordUpdated = true;
        }

        @Override
        public User save(User user) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Long> findIdByUsernameAndRole(String username, UserRole role) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean existsByIdAndRole(long id, UserRole role) {
            throw new UnsupportedOperationException();
        }
    }
}
