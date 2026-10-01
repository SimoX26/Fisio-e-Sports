package it.SimoSW.controller.graphic;

import it.SimoSW.controller.application.AuthenticationController;
import it.SimoSW.exception.AuthenticationFailedException;
import it.SimoSW.model.User;
import it.SimoSW.model.UserRole;
import it.SimoSW.util.SessionCookieService;
import it.SimoSW.util.bootstrap.ApplicationInitializer;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

@WebServlet("/login")
public class LoginPageServlet extends HttpServlet {

    private AuthenticationController authenticationController;

    @Override
    public void init() {
        ApplicationInitializer initializer =
                (ApplicationInitializer) getServletContext().getAttribute("appInitializer");
        this.authenticationController = initializer.getAuthenticationController();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("loggedUser") != null && session.getAttribute("userRole") != null) {
            String userRole = String.valueOf(session.getAttribute("userRole"));
            if (UserRole.ADMIN.name().equals(userRole)) {
                response.sendRedirect(request.getContextPath() + "/admin");
            } else {
                response.sendRedirect(request.getContextPath() + "/dashboard");
            }
            return;
        }

        request.getRequestDispatcher("/WEB-INF/jsp/login.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String username = request.getParameter("username");
        String password = request.getParameter("password");

        try {
            User user = authenticationController.authenticate(username, password);

            if (!user.isActive()) {
                throw new AuthenticationFailedException("User disabled");
            }

            HttpSession session = request.getSession(true);
            session.setAttribute("loggedUser", user.getUsername());
            session.setAttribute("userRole", user.getRole().name());

            try {
                String rememberMeToken = authenticationController.createRememberMeToken(user);
                SessionCookieService.addRememberMeCookie(request, response, rememberMeToken);
            } catch (RuntimeException ex) {
                getServletContext().log("Remember-me token non disponibile, continuo senza cookie persistente", ex);
            }

            if (user.getRole() == UserRole.ADMIN) {
                response.sendRedirect(request.getContextPath() + "/admin");
            } else {
                response.sendRedirect(request.getContextPath() + "/dashboard");
            }

        } catch (AuthenticationFailedException ex) {
            request.setAttribute("error", "Credenziali non valide");
            request.setAttribute("username", username);
            request.getRequestDispatcher("/WEB-INF/jsp/login.jsp").forward(request, response);
        } catch (RuntimeException ex) {
            request.setAttribute("error", "Errore tecnico durante il login. Verifica database e configurazione.");
            request.setAttribute("username", username);
            request.getRequestDispatcher("/WEB-INF/jsp/login.jsp").forward(request, response);
        }
    }
}
