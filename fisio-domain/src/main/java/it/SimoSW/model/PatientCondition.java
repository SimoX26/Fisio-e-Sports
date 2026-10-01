package it.SimoSW.model;

public class PatientCondition {

    private long anamnesisId;
    private ConditionCategory category;
    private String code;
    private String label;
    private String status;
    private String notes;

    public long getAnamnesisId() {
        return anamnesisId;
    }

    public void setAnamnesisId(long anamnesisId) {
        this.anamnesisId = anamnesisId;
    }

    public ConditionCategory getCategory() {
        return category;
    }

    public void setCategory(ConditionCategory category) {
        this.category = category;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
