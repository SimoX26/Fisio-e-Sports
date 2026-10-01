package it.SimoSW.util;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public final class SessionCookieService {

    public static final String REMEMBER_ME_COOKIE = "REMEMBER_ME_TOKEN";
    public static final int REMEMBER_ME_MAX_AGE_SECONDS = 30 * 24 * 60 * 60;

    private SessionCookieService() {
    }

    public static void addRememberMeCookie(HttpServletRequest request, HttpServletResponse response, String token) {
        Cookie cookie = new Cookie(REMEMBER_ME_COOKIE, token);
        cookie.setHttpOnly(true);
        cookie.setSecure(request.isSecure());
        cookie.setPath(resolveCookiePath(request));
        cookie.setMaxAge(REMEMBER_ME_MAX_AGE_SECONDS);
        response.addCookie(cookie);
    }

    public static void clearRememberMeCookie(HttpServletRequest request, HttpServletResponse response) {
        Cookie cookie = new Cookie(REMEMBER_ME_COOKIE, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(request.isSecure());
        cookie.setPath(resolveCookiePath(request));
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    public static String extractRememberMeToken(Cookie[] cookies) {
        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (REMEMBER_ME_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }

    private static String resolveCookiePath(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        return contextPath == null || contextPath.isBlank() ? "/" : contextPath;
    }
}
