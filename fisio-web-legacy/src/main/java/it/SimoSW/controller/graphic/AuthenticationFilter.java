package it.SimoSW.controller.graphic;

import it.SimoSW.controller.application.AuthenticationController;
import it.SimoSW.model.User;
import it.SimoSW.model.UserRole;
import it.SimoSW.util.SessionCookieService;
import it.SimoSW.util.bootstrap.ApplicationInitializer;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Optional;

@WebFilter("/*")
public class AuthenticationFilter implements Filter {

    private AuthenticationController authenticationController;

    @Override
    public void init(FilterConfig filterConfig) {
        ApplicationInitializer initializer =
                (ApplicationInitializer) filterConfig.getServletContext().getAttribute("appInitializer");
        this.authenticationController = initializer.getAuthenticationController();
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        request.setCharacterEncoding("UTF-8");
        response.setCharacterEncoding("UTF-8");
        String path = normalizePath(request);

        if (isStaticOrTechnicalPath(path)) {
            chain.doFilter(request, response);
            return;
        }

        ensureSessionFromRememberMeCookie(request, response);
        HttpSession session = request.getSession(false);
        boolean authenticated = hasAuthenticatedSession(session);

        if (isPublicPath(path)) {
            if (authenticated && isLandingOrAuthPage(path)) {
                redirectToHomeForRole(request, response, String.valueOf(session.getAttribute("userRole")));
                return;
            }

            chain.doFilter(request, response);
            return;
        }

        if (!authenticated) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        if (!isAuthorizedForPath(path, String.valueOf(session.getAttribute("userRole")))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        chain.doFilter(request, response);
    }

    private void ensureSessionFromRememberMeCookie(HttpServletRequest request, HttpServletResponse response) {
        HttpSession existingSession = request.getSession(false);
        if (hasAuthenticatedSession(existingSession)) {
            return;
        }

        String rawToken = SessionCookieService.extractRememberMeToken(request.getCookies());
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }

        Optional<User> rememberedUser = authenticationController.authenticateByRememberMeToken(rawToken);
        if (rememberedUser.isEmpty()) {
            SessionCookieService.clearRememberMeCookie(request, response);
            return;
        }

        User user = rememberedUser.get();
        HttpSession session = request.getSession(true);
        session.setAttribute("loggedUser", user.getUsername());
        session.setAttribute("userRole", user.getRole().name());
    }

    private boolean hasAuthenticatedSession(HttpSession session) {
        if (session == null) {
            return false;
        }
        Object loggedUser = session.getAttribute("loggedUser");
        Object userRole = session.getAttribute("userRole");
        return loggedUser != null && userRole != null;
    }

    private boolean isPublicPath(String path) {
        return "/".equals(path)
                || "/index.jsp".equals(path)
                || "/login".equals(path)
                || "/register".equals(path);
    }

    private boolean isLandingOrAuthPage(String path) {
        return "/".equals(path)
                || "/index.jsp".equals(path)
                || "/login".equals(path)
                || "/register".equals(path);
    }

    private boolean isStaticOrTechnicalPath(String path) {
        return path.startsWith("/assets/")
                || "/manifest.webmanifest".equals(path)
                || "/site.webmanifest".equals(path)
                || path.startsWith("/favicon")
                || path.startsWith("/javax.faces.resource/");
    }

    private boolean isAuthorizedForPath(String path, String role) {
        if ("/logout".equals(path) || path.startsWith("/settings")) {
            return true;
        }

        if (path.startsWith("/admin")) {
            return UserRole.ADMIN.name().equals(role);
        }

        return UserRole.THERAPIST.name().equals(role);
    }

    private void redirectToHomeForRole(HttpServletRequest request, HttpServletResponse response, String role)
            throws IOException {
        if (UserRole.ADMIN.name().equals(role)) {
            response.sendRedirect(request.getContextPath() + "/admin");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/dashboard");
    }

    private String normalizePath(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        String uri = request.getRequestURI();
        String path = uri.substring(contextPath.length());
        return path.isEmpty() ? "/" : path;
    }
}
