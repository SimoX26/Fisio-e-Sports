package it.SimoSW.exception;

public class AppointmentNotFoundException extends RuntimeException {
    public AppointmentNotFoundException(long id) {
        super("Appointment not found: " + id);
    }
}
