package it.SimoSW.exception;

public class TreatmentSessionNotFoundException extends RuntimeException {
    public TreatmentSessionNotFoundException(long id) {
        super("Treatment session not found: " + id);
    }
}
