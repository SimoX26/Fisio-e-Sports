package it.SimoSW.model.dao;

import it.SimoSW.model.PatientCondition;

import java.util.List;

public interface PatientConditionDAO {

    void saveAll(long anamnesisId, List<PatientCondition> conditions);

    List<PatientCondition> findByAnamnesisId(long anamnesisId);
}
