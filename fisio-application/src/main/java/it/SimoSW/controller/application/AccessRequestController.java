package it.SimoSW.controller.application;

import it.SimoSW.exception.InvalidUserDataException;
import it.SimoSW.exception.UsernameAlreadyExistsException;
import it.SimoSW.model.AccessRequest;
import it.SimoSW.model.AccessRequestStatus;
import it.SimoSW.model.User;
import it.SimoSW.model.UserRole;
import it.SimoSW.model.dao.AccessRequestDAO;
import it.SimoSW.model.dao.UserDAO;
import it.SimoSW.util.PasswordHasher;

import java.util.List;

public class AccessRequestController {

    private static final int RECENT_REQUESTS_LIMIT = 50;

    private final AccessRequestDAO accessRequestDAO;
    private final UserDAO userDAO;

    public AccessRequestController(AccessRequestDAO accessRequestDAO, UserDAO userDAO) {
        this.accessRequestDAO = accessRequestDAO;
        this.userDAO = userDAO;
    }

    public void submitAccessRequest(
            String firstName,
            String lastName,
            String email,
            String username,
            String plainPassword
    ) {
        validateRequestData(firstName, lastName, email, username, plainPassword);

        if (userDAO.findByUsername(username).isPresent()) {
            throw new UsernameAlreadyExistsException(username);
        }

        if (accessRequestDAO.existsPendingByUsernameOrEmail(username, email)) {
            throw new InvalidUserDataException("Esiste già una richiesta in attesa con username o email indicati");
        }

        AccessRequest request = new AccessRequest();
        request.setFirstName(firstName.trim());
        request.setLastName(lastName.trim());
        request.setEmail(email.trim().toLowerCase());
        request.setUsername(username.trim());
        request.setPasswordHash(PasswordHasher.hashPassword(plainPassword));
        request.setRequestedRole(UserRole.THERAPIST);
        request.setStatus(AccessRequestStatus.PENDING);

        accessRequestDAO.save(request);
    }

    public List<AccessRequest> getPendingRequests() {
        return accessRequestDAO.findByStatus(AccessRequestStatus.PENDING);
    }

    public List<AccessRequest> getRecentRequests() {
        return accessRequestDAO.findRecent(RECENT_REQUESTS_LIMIT);
    }

    public void approve(long requestId, String reviewerAdminUsername) {
        AccessRequest request = accessRequestDAO.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Richiesta non trovata"));

        if (request.getStatus() != AccessRequestStatus.PENDING) {
            throw new IllegalStateException("La richiesta non è più in stato pending");
        }

        if (userDAO.findByUsername(request.getUsername()).isPresent()) {
            throw new UsernameAlreadyExistsException(request.getUsername());
        }

        Long reviewerId = resolveAdminUserId(reviewerAdminUsername);
        User user = new User(
                request.getUsername(),
                request.getPasswordHash(),
                request.getRequestedRole(),
                true
        );
        userDAO.save(user);
        accessRequestDAO.updateStatus(requestId, AccessRequestStatus.APPROVED, reviewerId);
    }

    public void reject(long requestId, String reviewerAdminUsername) {
        AccessRequest request = accessRequestDAO.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Richiesta non trovata"));

        if (request.getStatus() != AccessRequestStatus.PENDING) {
            throw new IllegalStateException("La richiesta non è più in stato pending");
        }

        Long reviewerId = resolveAdminUserId(reviewerAdminUsername);
        accessRequestDAO.updateStatus(requestId, AccessRequestStatus.REJECTED, reviewerId);
    }

    private Long resolveAdminUserId(String reviewerAdminUsername) {
        if (reviewerAdminUsername == null || reviewerAdminUsername.isBlank()) {
            return null;
        }

        return userDAO.findIdByUsernameAndRole(reviewerAdminUsername, UserRole.ADMIN)
                .orElse(null);
    }

    private void validateRequestData(
            String firstName,
            String lastName,
            String email,
            String username,
            String password
    ) {
        if (firstName == null || firstName.isBlank()) {
            throw new InvalidUserDataException("Nome obbligatorio");
        }
        if (lastName == null || lastName.isBlank()) {
            throw new InvalidUserDataException("Cognome obbligatorio");
        }
        if (email == null || email.isBlank() || !email.contains("@")) {
            throw new InvalidUserDataException("Email non valida");
        }
        if (username == null || username.isBlank()) {
            throw new InvalidUserDataException("Username obbligatorio");
        }
        if (password == null || password.length() < 6) {
            throw new InvalidUserDataException("La password deve contenere almeno 6 caratteri");
        }
    }
}
