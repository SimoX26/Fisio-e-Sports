package it.SimoSW.controller.graphic;

import it.SimoSW.controller.application.AuthenticationController;
import it.SimoSW.util.SessionCookieService;
import it.SimoSW.util.bootstrap.ApplicationInitializer;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

@WebServlet("/logout")
public class LogoutServlet extends HttpServlet {

    private AuthenticationController authenticationController;

    @Override
    public void init() {
        ApplicationInitializer initializer =
                (ApplicationInitializer) getServletContext().getAttribute("appInitializer");
        this.authenticationController = initializer.getAuthenticationController();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);
        request.getRequestDispatcher("/WEB-INF/jsp/logoutConfirm.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!"1".equals(request.getParameter("confirmLogout"))) {
            response.sendRedirect(request.getContextPath() + "/logout?error=confirm_required");
            return;
        }
        performLogout(request, response);
    }

    private void performLogout(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String rememberMeToken = SessionCookieService.extractRememberMeToken(request.getCookies());
        if (rememberMeToken != null && !rememberMeToken.isBlank()) {
            authenticationController.revokeRememberMeToken(rememberMeToken);
        }
        SessionCookieService.clearRememberMeCookie(request, response);

        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }

        response.sendRedirect(request.getContextPath() + "/");
    }
}
