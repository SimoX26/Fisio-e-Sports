package it.SimoSW.util.bootstrap;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

@WebListener
public class ApplicationContextListener implements ServletContextListener {

    private KpiSnapshotScheduler kpiSnapshotScheduler;

    @Override
    public void contextInitialized(ServletContextEvent sce) {

        ApplicationInitializer initializer = new ApplicationInitializer();
        initializer.init();

        kpiSnapshotScheduler = new KpiSnapshotScheduler(initializer.getKpiSnapshotController());
        kpiSnapshotScheduler.start();
        kpiSnapshotScheduler.runNow();

        ServletContext context = sce.getServletContext();
        context.setAttribute("appInitializer", initializer);
        context.setAttribute("kpiSnapshotScheduler", kpiSnapshotScheduler);
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        if (kpiSnapshotScheduler != null) {
            kpiSnapshotScheduler.stop();
        }
    }
}
