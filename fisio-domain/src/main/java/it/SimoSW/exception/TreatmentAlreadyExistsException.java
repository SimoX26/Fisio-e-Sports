package it.SimoSW.exception;

public class TreatmentAlreadyExistsException extends RuntimeException {
    public TreatmentAlreadyExistsException(long appointmentId) {
        super("Treatment session already exists for appointment: " + appointmentId);
    }
}
