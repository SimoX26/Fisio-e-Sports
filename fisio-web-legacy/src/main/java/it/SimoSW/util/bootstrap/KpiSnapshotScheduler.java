package it.SimoSW.util.bootstrap;

import it.SimoSW.controller.application.KpiSnapshotController;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class KpiSnapshotScheduler {

    private static final Logger LOGGER = Logger.getLogger(KpiSnapshotScheduler.class.getName());
    private static final int DEFAULT_RUN_HOUR = 2;
    private static final int DEFAULT_RUN_MINUTE = 30;

    private final KpiSnapshotController kpiSnapshotController;
    private ScheduledExecutorService executor;

    public KpiSnapshotScheduler(KpiSnapshotController kpiSnapshotController) {
        if (kpiSnapshotController == null) {
            throw new IllegalArgumentException("kpiSnapshotController non può essere null");
        }
        this.kpiSnapshotController = kpiSnapshotController;
    }

    public synchronized void start() {
        if (executor != null && !executor.isShutdown()) {
            return;
        }

        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "kpi-snapshot-scheduler");
            thread.setDaemon(true);
            return thread;
        });

        long initialDelaySeconds = computeInitialDelaySeconds(DEFAULT_RUN_HOUR, DEFAULT_RUN_MINUTE);
        long periodSeconds = TimeUnit.DAYS.toSeconds(1);

        LOGGER.info(() -> "KPI snapshot scheduler avviato: prima esecuzione tra " + initialDelaySeconds + "s");
        executor.scheduleAtFixedRate(this::safeRunRefresh, initialDelaySeconds, periodSeconds, TimeUnit.SECONDS);
    }

    public synchronized void stop() {
        if (executor == null) {
            return;
        }
        executor.shutdownNow();
        executor = null;
        LOGGER.info("KPI snapshot scheduler arrestato");
    }

    public void runNow() {
        safeRunRefresh();
    }

    private void safeRunRefresh() {
        try {
            LOGGER.info("KPI snapshot refresh avviato (mese corrente + precedente)");
            kpiSnapshotController.refreshCurrentAndPreviousMonthSnapshots();
            LOGGER.info("KPI snapshot refresh completato");
        } catch (RuntimeException ex) {
            LOGGER.log(Level.SEVERE, "Errore durante KPI snapshot refresh", ex);
        }
    }

    private long computeInitialDelaySeconds(int hour, int minute) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRun = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0);
        if (!nextRun.isAfter(now)) {
            nextRun = nextRun.plusDays(1);
        }
        return Duration.between(now, nextRun).getSeconds();
    }
}
