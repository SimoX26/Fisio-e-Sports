package it.SimoSW.model;

import java.time.LocalDateTime;

public class TreatmentSession {

    private long id;
    private long treatmentPlanId;
    private Long appointmentId;
    private Long patientId;
    private long therapistId;
    private LocalDateTime start;
    private LocalDateTime end;
    private Integer painScorePre;
    private Integer painScorePost;
    private String sessionOutcome;
    private String homeExercises;
    private String notes;
    private TreatmentSessionState state;

    public TreatmentSession() {
    }

    public TreatmentSession(long id, long treatmentPlanId, Long appointmentId,
                            Long patientId, long therapistId) {
        this.id = id;
        this.treatmentPlanId = treatmentPlanId;
        this.appointmentId = appointmentId;
        this.patientId = patientId;
        this.therapistId = therapistId;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getTreatmentPlanId() {
        return treatmentPlanId;
    }

    public void setTreatmentPlanId(long treatmentPlanId) {
        this.treatmentPlanId = treatmentPlanId;
    }

    public Long getAppointmentId() {
        return appointmentId;
    }

    public void setAppointmentId(Long appointmentId) {
        this.appointmentId = appointmentId;
    }

    public Long getPatientId() {
        return patientId;
    }

    public void setPatientId(Long patientId) {
        this.patientId = patientId;
    }

    public long getTherapistId() {
        return therapistId;
    }

    public void setTherapistId(long therapistId) {
        this.therapistId = therapistId;
    }

    public LocalDateTime getStart() {
        return start;
    }

    public void setStart(LocalDateTime start) {
        this.start = start;
    }

    public LocalDateTime getEnd() {
        return end;
    }

    public void setEnd(LocalDateTime end) {
        this.end = end;
    }

    public Integer getPainScorePre() {
        return painScorePre;
    }

    public void setPainScorePre(Integer painScorePre) {
        this.painScorePre = painScorePre;
    }

    public Integer getPainScorePost() {
        return painScorePost;
    }

    public void setPainScorePost(Integer painScorePost) {
        this.painScorePost = painScorePost;
    }

    public String getSessionOutcome() {
        return sessionOutcome;
    }

    public void setSessionOutcome(String sessionOutcome) {
        this.sessionOutcome = sessionOutcome;
    }

    public String getHomeExercises() {
        return homeExercises;
    }

    public void setHomeExercises(String homeExercises) {
        this.homeExercises = homeExercises;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public TreatmentSessionState getState() {
        return state;
    }

    public void setState(TreatmentSessionState state) {
        this.state = state;
    }
}
