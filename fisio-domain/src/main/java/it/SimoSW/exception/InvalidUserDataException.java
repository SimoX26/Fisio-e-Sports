package it.SimoSW.exception;

/**
 * Thrown to indicate that user data violates domain constraints.
 *
 * <p>This exception is raised when one or more user attributes
 * (such as username, password or role) are invalid according
 * to the business rules of the system.</p>
 */
public class InvalidUserDataException extends RuntimeException {
    public InvalidUserDataException(String message) {
        super(message);
    }}
