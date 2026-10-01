package it.SimoSW.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class TreatmentPlan {

    private long id;
    private Long patientId;
    private long therapistId;
    private String title;
    private String goals;
    private Integer frequencyPerWeek;
    private LocalDate startDate;
    private LocalDate expectedEndDate;
    private int totalSessionsPlanned;
    private TreatmentPlanState state;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getGoals() {
        return goals;
    }

    public void setGoals(String goals) {
        this.goals = goals;
    }

    public Integer getFrequencyPerWeek() {
        return frequencyPerWeek;
    }

    public void setFrequencyPerWeek(Integer frequencyPerWeek) {
        this.frequencyPerWeek = frequencyPerWeek;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getExpectedEndDate() {
        return expectedEndDate;
    }

    public void setExpectedEndDate(LocalDate expectedEndDate) {
        this.expectedEndDate = expectedEndDate;
    }

    public int getTotalSessionsPlanned() {
        return totalSessionsPlanned;
    }

    public void setTotalSessionsPlanned(int totalSessionsPlanned) {
        this.totalSessionsPlanned = totalSessionsPlanned;
    }

    public TreatmentPlanState getState() {
        return state;
    }

    public void setState(TreatmentPlanState state) {
        this.state = state;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
