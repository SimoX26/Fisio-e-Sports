package it.SimoSW.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class PatientAnamnesis {

    private long id;
    private long patientId;
    private long therapistId;
    private LocalDate assessmentDate;

    private String chiefComplaint;
    private String painLocation;
    private String painQuality;
    private String associatedSymptoms;

    private String onsetType;
    private String onsetContext;
    private Boolean disabling;
    private String painFrequency;
    private String painProgression;
    private String painWithMovement;
    private String painWithRest;
    private Boolean nightPain;
    private Boolean morningPain;
    private Integer painIntensity;
    private Boolean usesPainMeds;
    private String painMedsEffect;

    private String clinicalTests;
    private String specialistVisits;
    private String previousTreatments;
    private String pathologyHistory;
    private String currentRegularDrugs;
    private String surgeryHistory;
    private String traumaHistory;
    private String devicesHistory;
    private Boolean chewingDisorders;
    private String majorInfectionsHistory;
    private String familyHistory;

    private BigDecimal heightCm;
    private BigDecimal weightKg;
    private String lifestyle;
    private String sportPractice;
    private String substanceUse;
    private Integer sleepQuality;
    private Integer stressLevel;
    private String dietQuality;
    private String femaleCycleNotes;

    private String freeNotesJson;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getPatientId() {
        return patientId;
    }

    public void setPatientId(long patientId) {
        this.patientId = patientId;
    }

    public long getTherapistId() {
        return therapistId;
    }

    public void setTherapistId(long therapistId) {
        this.therapistId = therapistId;
    }

    public LocalDate getAssessmentDate() {
        return assessmentDate;
    }

    public void setAssessmentDate(LocalDate assessmentDate) {
        this.assessmentDate = assessmentDate;
    }

    public String getChiefComplaint() {
        return chiefComplaint;
    }

    public void setChiefComplaint(String chiefComplaint) {
        this.chiefComplaint = chiefComplaint;
    }

    public String getPainLocation() {
        return painLocation;
    }

    public void setPainLocation(String painLocation) {
        this.painLocation = painLocation;
    }

    public String getPainQuality() {
        return painQuality;
    }

    public void setPainQuality(String painQuality) {
        this.painQuality = painQuality;
    }

    public String getAssociatedSymptoms() {
        return associatedSymptoms;
    }

    public void setAssociatedSymptoms(String associatedSymptoms) {
        this.associatedSymptoms = associatedSymptoms;
    }

    public String getOnsetType() {
        return onsetType;
    }

    public void setOnsetType(String onsetType) {
        this.onsetType = onsetType;
    }

    public String getOnsetContext() {
        return onsetContext;
    }

    public void setOnsetContext(String onsetContext) {
        this.onsetContext = onsetContext;
    }

    public Boolean getDisabling() {
        return disabling;
    }

    public void setDisabling(Boolean disabling) {
        this.disabling = disabling;
    }

    public String getPainFrequency() {
        return painFrequency;
    }

    public void setPainFrequency(String painFrequency) {
        this.painFrequency = painFrequency;
    }

    public String getPainProgression() {
        return painProgression;
    }

    public void setPainProgression(String painProgression) {
        this.painProgression = painProgression;
    }

    public String getPainWithMovement() {
        return painWithMovement;
    }

    public void setPainWithMovement(String painWithMovement) {
        this.painWithMovement = painWithMovement;
    }

    public String getPainWithRest() {
        return painWithRest;
    }

    public void setPainWithRest(String painWithRest) {
        this.painWithRest = painWithRest;
    }

    public Boolean getNightPain() {
        return nightPain;
    }

    public void setNightPain(Boolean nightPain) {
        this.nightPain = nightPain;
    }

    public Boolean getMorningPain() {
        return morningPain;
    }

    public void setMorningPain(Boolean morningPain) {
        this.morningPain = morningPain;
    }

    public Integer getPainIntensity() {
        return painIntensity;
    }

    public void setPainIntensity(Integer painIntensity) {
        this.painIntensity = painIntensity;
    }

    public Boolean getUsesPainMeds() {
        return usesPainMeds;
    }

    public void setUsesPainMeds(Boolean usesPainMeds) {
        this.usesPainMeds = usesPainMeds;
    }

    public String getPainMedsEffect() {
        return painMedsEffect;
    }

    public void setPainMedsEffect(String painMedsEffect) {
        this.painMedsEffect = painMedsEffect;
    }

    public String getClinicalTests() {
        return clinicalTests;
    }

    public void setClinicalTests(String clinicalTests) {
        this.clinicalTests = clinicalTests;
    }

    public String getSpecialistVisits() {
        return specialistVisits;
    }

    public void setSpecialistVisits(String specialistVisits) {
        this.specialistVisits = specialistVisits;
    }

    public String getPreviousTreatments() {
        return previousTreatments;
    }

    public void setPreviousTreatments(String previousTreatments) {
        this.previousTreatments = previousTreatments;
    }

    public String getPathologyHistory() {
        return pathologyHistory;
    }

    public void setPathologyHistory(String pathologyHistory) {
        this.pathologyHistory = pathologyHistory;
    }

    public String getCurrentRegularDrugs() {
        return currentRegularDrugs;
    }

    public void setCurrentRegularDrugs(String currentRegularDrugs) {
        this.currentRegularDrugs = currentRegularDrugs;
    }

    public String getSurgeryHistory() {
        return surgeryHistory;
    }

    public void setSurgeryHistory(String surgeryHistory) {
        this.surgeryHistory = surgeryHistory;
    }

    public String getTraumaHistory() {
        return traumaHistory;
    }

    public void setTraumaHistory(String traumaHistory) {
        this.traumaHistory = traumaHistory;
    }

    public String getDevicesHistory() {
        return devicesHistory;
    }

    public void setDevicesHistory(String devicesHistory) {
        this.devicesHistory = devicesHistory;
    }

    public Boolean getChewingDisorders() {
        return chewingDisorders;
    }

    public void setChewingDisorders(Boolean chewingDisorders) {
        this.chewingDisorders = chewingDisorders;
    }

    public String getMajorInfectionsHistory() {
        return majorInfectionsHistory;
    }

    public void setMajorInfectionsHistory(String majorInfectionsHistory) {
        this.majorInfectionsHistory = majorInfectionsHistory;
    }

    public String getFamilyHistory() {
        return familyHistory;
    }

    public void setFamilyHistory(String familyHistory) {
        this.familyHistory = familyHistory;
    }

    public BigDecimal getHeightCm() {
        return heightCm;
    }

    public void setHeightCm(BigDecimal heightCm) {
        this.heightCm = heightCm;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }

    public String getLifestyle() {
        return lifestyle;
    }

    public void setLifestyle(String lifestyle) {
        this.lifestyle = lifestyle;
    }

    public String getSportPractice() {
        return sportPractice;
    }

    public void setSportPractice(String sportPractice) {
        this.sportPractice = sportPractice;
    }

    public String getSubstanceUse() {
        return substanceUse;
    }

    public void setSubstanceUse(String substanceUse) {
        this.substanceUse = substanceUse;
    }

    public Integer getSleepQuality() {
        return sleepQuality;
    }

    public void setSleepQuality(Integer sleepQuality) {
        this.sleepQuality = sleepQuality;
    }

    public Integer getStressLevel() {
        return stressLevel;
    }

    public void setStressLevel(Integer stressLevel) {
        this.stressLevel = stressLevel;
    }

    public String getDietQuality() {
        return dietQuality;
    }

    public void setDietQuality(String dietQuality) {
        this.dietQuality = dietQuality;
    }

    public String getFemaleCycleNotes() {
        return femaleCycleNotes;
    }

    public void setFemaleCycleNotes(String femaleCycleNotes) {
        this.femaleCycleNotes = femaleCycleNotes;
    }

    public String getFreeNotesJson() {
        return freeNotesJson;
    }

    public void setFreeNotesJson(String freeNotesJson) {
        this.freeNotesJson = freeNotesJson;
    }
}
