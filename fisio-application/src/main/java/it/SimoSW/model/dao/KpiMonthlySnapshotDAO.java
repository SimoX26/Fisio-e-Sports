package it.SimoSW.model.dao;

import it.SimoSW.model.KpiMonthlySnapshot;

import java.util.List;

public interface KpiMonthlySnapshotDAO {

    void saveOrUpdate(KpiMonthlySnapshot snapshot);

    List<KpiMonthlySnapshot> findRecentGlobal(int limitMonths);

    List<KpiMonthlySnapshot> findRecentByTherapist(long therapistId, int limitMonths);
}

