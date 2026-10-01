package it.SimoSW.exception;

public class PatientNotFoundException extends RuntimeException {
    public PatientNotFoundException(long id) {
        super("Patient not found: " + id);
    }
}
