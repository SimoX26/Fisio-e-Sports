package it.SimoSW.controller.graphic;

import it.SimoSW.controller.application.AccessRequestController;
import it.SimoSW.util.bootstrap.ApplicationInitializer;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet("/admin")
public class AdminDashboardServlet extends HttpServlet {

    private AccessRequestController accessRequestController;

    @Override
    public void init() {
        ApplicationInitializer initializer =
                (ApplicationInitializer) getServletContext().getAttribute("appInitializer");
        this.accessRequestController = initializer.getAccessRequestController();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setAttribute("pendingCount", accessRequestController.getPendingRequests().size());
        request.getRequestDispatcher("/WEB-INF/jsp/admin/adminDashboard.jsp").forward(request, response);
    }
}
