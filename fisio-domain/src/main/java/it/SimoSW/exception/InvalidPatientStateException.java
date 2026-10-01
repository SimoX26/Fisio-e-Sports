package it.SimoSW.exception;

public class InvalidPatientStateException extends RuntimeException {
    public InvalidPatientStateException(String message) {
        super(message);
    }
}
