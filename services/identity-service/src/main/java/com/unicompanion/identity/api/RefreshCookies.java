package com.unicompanion.identity.api;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

final class RefreshCookies {

    static final String NAME = "uc_refresh";

    private RefreshCookies() {
    }

    static void set(HttpServletRequest request, HttpServletResponse response, String refreshToken, long maxAgeSeconds) {
        Cookie cookie = base(request, refreshToken);
        cookie.setMaxAge((int) Math.min(maxAgeSeconds, Integer.MAX_VALUE));
        response.addCookie(cookie);
    }

    static void clear(HttpServletRequest request, HttpServletResponse response) {
        Cookie cookie = base(request, "");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    static String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (NAME.equals(cookie.getName())) {
                String value = cookie.getValue();
                return value == null || value.isBlank() ? null : value;
            }
        }
        return null;
    }

    private static Cookie base(HttpServletRequest request, String value) {
        Cookie cookie = new Cookie(NAME, value);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setAttribute("SameSite", "Lax");
        cookie.setSecure(isSecure(request));
        return cookie;
    }

    private static boolean isSecure(HttpServletRequest request) {
        if (request.isSecure()) {
            return true;
        }
        String forwarded = request.getHeader("X-Forwarded-Proto");
        return forwarded != null && forwarded.equalsIgnoreCase("https");
    }
}
