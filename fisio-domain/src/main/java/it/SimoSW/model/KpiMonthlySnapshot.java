package it.SimoSW.model;

import java.time.LocalDateTime;

public class KpiMonthlySnapshot {

    private long id;
    private String scopeType;
    private long scopeId;
    private Long therapistId;
    private int year;
    private int month;

    private int appointmentsCreated;
    private int appointmentsCompleted;
    private int appointmentsCancelled;
    private int activePatientsMonth;
    private int newPatientsMonth;
    private int treatmentPlansStarted;
    private int treatmentSessionsCompleted;
    private int totalBookedMinutes;
    private int appointmentsInMonth;
    private int newPatientsFirstAppointmentMonth;
    private int returningPatientsMonth;
    private double agendaSaturationPct;
    private double appointmentsPerActivePatient;

    private LocalDateTime computedAt;
    private String sourceVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getScopeType() {
        return scopeType;
    }

    public void setScopeType(String scopeType) {
        this.scopeType = scopeType;
    }

    public long getScopeId() {
        return scopeId;
    }

    public void setScopeId(long scopeId) {
        this.scopeId = scopeId;
    }

    public Long getTherapistId() {
        return therapistId;
    }

    public void setTherapistId(Long therapistId) {
        this.therapistId = therapistId;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public int getMonth() {
        return month;
    }

    public void setMonth(int month) {
        this.month = month;
    }

    public int getAppointmentsCreated() {
        return appointmentsCreated;
    }

    public void setAppointmentsCreated(int appointmentsCreated) {
        this.appointmentsCreated = appointmentsCreated;
    }

    public int getAppointmentsCompleted() {
        return appointmentsCompleted;
    }

    public void setAppointmentsCompleted(int appointmentsCompleted) {
        this.appointmentsCompleted = appointmentsCompleted;
    }

    public int getAppointmentsCancelled() {
        return appointmentsCancelled;
    }

    public void setAppointmentsCancelled(int appointmentsCancelled) {
        this.appointmentsCancelled = appointmentsCancelled;
    }

    public int getActivePatientsMonth() {
        return activePatientsMonth;
    }

    public void setActivePatientsMonth(int activePatientsMonth) {
        this.activePatientsMonth = activePatientsMonth;
    }

    public int getNewPatientsMonth() {
        return newPatientsMonth;
    }

    public void setNewPatientsMonth(int newPatientsMonth) {
        this.newPatientsMonth = newPatientsMonth;
    }

    public int getTreatmentPlansStarted() {
        return treatmentPlansStarted;
    }

    public void setTreatmentPlansStarted(int treatmentPlansStarted) {
        this.treatmentPlansStarted = treatmentPlansStarted;
    }

    public int getTreatmentSessionsCompleted() {
        return treatmentSessionsCompleted;
    }

    public void setTreatmentSessionsCompleted(int treatmentSessionsCompleted) {
        this.treatmentSessionsCompleted = treatmentSessionsCompleted;
    }

    public int getTotalBookedMinutes() {
        return totalBookedMinutes;
    }

    public void setTotalBookedMinutes(int totalBookedMinutes) {
        this.totalBookedMinutes = totalBookedMinutes;
    }

    public int getAppointmentsInMonth() {
        return appointmentsInMonth;
    }

    public void setAppointmentsInMonth(int appointmentsInMonth) {
        this.appointmentsInMonth = appointmentsInMonth;
    }

    public int getNewPatientsFirstAppointmentMonth() {
        return newPatientsFirstAppointmentMonth;
    }

    public void setNewPatientsFirstAppointmentMonth(int newPatientsFirstAppointmentMonth) {
        this.newPatientsFirstAppointmentMonth = newPatientsFirstAppointmentMonth;
    }

    public int getReturningPatientsMonth() {
        return returningPatientsMonth;
    }

    public void setReturningPatientsMonth(int returningPatientsMonth) {
        this.returningPatientsMonth = returningPatientsMonth;
    }

    public double getAgendaSaturationPct() {
        return agendaSaturationPct;
    }

    public void setAgendaSaturationPct(double agendaSaturationPct) {
        this.agendaSaturationPct = agendaSaturationPct;
    }

    public double getAppointmentsPerActivePatient() {
        return appointmentsPerActivePatient;
    }

    public void setAppointmentsPerActivePatient(double appointmentsPerActivePatient) {
        this.appointmentsPerActivePatient = appointmentsPerActivePatient;
    }

    public LocalDateTime getComputedAt() {
        return computedAt;
    }

    public void setComputedAt(LocalDateTime computedAt) {
        this.computedAt = computedAt;
    }

    public String getSourceVersion() {
        return sourceVersion;
    }

    public void setSourceVersion(String sourceVersion) {
        this.sourceVersion = sourceVersion;
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
