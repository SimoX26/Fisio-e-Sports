package it.SimoSW.controller.graphic;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

final class PostSubmitNavigationGuard {

    private static final String SESSION_KEY = "postSubmitBlockedForms";

    private PostSubmitNavigationGuard() {
    }

    static void blockFormPageOnce(HttpServletRequest request, String formPath, String fallbackPath) {
        HttpSession session = request.getSession(true);
        Map<String, String> blockedForms = getBlockedForms(session);
        blockedForms.put(formPath, fallbackPath);
    }

    static boolean redirectIfBlocked(HttpServletRequest request, HttpServletResponse response, String formPath)
            throws IOException {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return false;
        }

        Map<String, String> blockedForms = getBlockedForms(session);
        String fallbackPath = blockedForms.remove(formPath);
        if (fallbackPath == null) {
            return false;
        }

        if (blockedForms.isEmpty()) {
            session.removeAttribute(SESSION_KEY);
        } else {
            session.setAttribute(SESSION_KEY, blockedForms);
        }

        response.sendRedirect(request.getContextPath() + fallbackPath);
        return true;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> getBlockedForms(HttpSession session) {
        Object attribute = session.getAttribute(SESSION_KEY);
        if (attribute instanceof Map) {
            return (Map<String, String>) attribute;
        }

        Map<String, String> blockedForms = new HashMap<>();
        session.setAttribute(SESSION_KEY, blockedForms);
        return blockedForms;
    }
}
