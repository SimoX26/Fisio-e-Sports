package it.SimoSW.exception;

public class TimeSlotNotAvailableException extends RuntimeException {
    public TimeSlotNotAvailableException() {
        super("Time slot not available");
    }
}
