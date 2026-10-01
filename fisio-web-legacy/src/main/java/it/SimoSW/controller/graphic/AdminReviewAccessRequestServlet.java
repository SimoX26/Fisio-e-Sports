package it.SimoSW.controller.graphic;

import it.SimoSW.controller.application.AccessRequestController;
import it.SimoSW.exception.UsernameAlreadyExistsException;
import it.SimoSW.util.bootstrap.ApplicationInitializer;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@WebServlet("/admin/access-requests/review")
public class AdminReviewAccessRequestServlet extends HttpServlet {

    private AccessRequestController accessRequestController;

    @Override
    public void init() {
        ApplicationInitializer initializer =
                (ApplicationInitializer) getServletContext().getAttribute("appInitializer");
        this.accessRequestController = initializer.getAccessRequestController();
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String requestIdRaw = request.getParameter("requestId");
        String action = request.getParameter("action");
        String adminUsername = String.valueOf(request.getSession().getAttribute("loggedUser"));

        try {
            long requestId = Long.parseLong(requestIdRaw);
            if ("approve".equalsIgnoreCase(action)) {
                accessRequestController.approve(requestId, adminUsername);
                response.sendRedirect(request.getContextPath() + "/admin/access-requests?success=approved");
                return;
            }
            if ("reject".equalsIgnoreCase(action)) {
                accessRequestController.reject(requestId, adminUsername);
                response.sendRedirect(request.getContextPath() + "/admin/access-requests?success=rejected");
                return;
            }

            response.sendRedirect(request.getContextPath() + "/admin/access-requests?error=invalid_action");
        } catch (NumberFormatException e) {
            response.sendRedirect(request.getContextPath() + "/admin/access-requests?error=invalid_request");
        } catch (UsernameAlreadyExistsException | IllegalArgumentException | IllegalStateException ex) {
            String encoded = URLEncoder.encode(ex.getMessage(), StandardCharsets.UTF_8);
            response.sendRedirect(request.getContextPath() + "/admin/access-requests?error=" + encoded);
        }
    }
}
