package it.SimoSW.controller.application;

import it.SimoSW.model.KpiMonthlySnapshot;
import it.SimoSW.model.dao.KpiMonthlySnapshotDAO;

import java.util.List;

public class KpiReadController {
    private final KpiMonthlySnapshotDAO snapshots;

    public KpiReadController(KpiMonthlySnapshotDAO snapshots) {
        this.snapshots = snapshots;
    }

    public List<KpiMonthlySnapshot> getRecentForTherapist(long therapistId, int months) {
        if (therapistId <= 0 || months < 1 || months > 36) {
            throw new IllegalArgumentException("Parametri KPI non validi");
        }
        return snapshots.findRecentByTherapist(therapistId, months);
    }
}
