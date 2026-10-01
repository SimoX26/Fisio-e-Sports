package it.SimoSW.exception;

/**
 * Thrown to indicate that a username already exists in the system.
 *
 * <p>This exception is raised when attempting to create a new user
 * with a username that is already present in the persistence layer.</p>
 */
public class UsernameAlreadyExistsException extends RuntimeException {

    private final String username;

    public UsernameAlreadyExistsException(String username) {
        super("Username already exists: " + username);
        this.username = username;
    }

    public String getUsername() {
        return username;
    }}
