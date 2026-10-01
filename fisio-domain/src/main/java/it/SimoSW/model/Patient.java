package it.SimoSW.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;

public class Patient {
    private static final DateTimeFormatter CREATED_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private long id;
    private long therapistId;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private PatientState state;
    private LocalDateTime createdAt;
    private int linkedAppointmentsCount;

    public Patient() {
    }

    public Patient(long id, String firstName, String lastName,
                   String email, String phone) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getTherapistId() {
        return therapistId;
    }

    public void setTherapistId(long therapistId) {
        this.therapistId = therapistId;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getFullName() {
        String first = repairMojibake(firstName == null ? "" : firstName.trim());
        String last = repairMojibake(lastName == null ? "" : lastName.trim());
        String fullName = (first + " " + last).trim();
        return fullName.isEmpty() ? "Paziente" : fullName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public PatientState getState() {
        return state;
    }

    public void setState(PatientState state) {
        this.state = state;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getCreatedDateLabel() {
        if (createdAt == null) {
            return "";
        }
        return CREATED_DATE_FORMATTER.format(createdAt.toLocalDate());
    }

    public int getLinkedAppointmentsCount() {
        return linkedAppointmentsCount;
    }

    public void setLinkedAppointmentsCount(int linkedAppointmentsCount) {
        this.linkedAppointmentsCount = Math.max(linkedAppointmentsCount, 0);
    }

    private String repairMojibake(String value) {
        if (value == null || value.isBlank() || !looksLikeMojibake(value)) {
            return value;
        }
        String repaired = new String(value.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
        return looksLikeMojibake(repaired) ? value : repaired;
    }

    private boolean looksLikeMojibake(String value) {
        return value.contains("Ã") || value.contains("Â") || value.contains("â") || value.contains("�");
    }
}
