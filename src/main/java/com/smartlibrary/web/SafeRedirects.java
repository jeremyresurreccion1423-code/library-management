package com.smartlibrary.web;

import jakarta.servlet.http.HttpServletRequest;

public final class SafeRedirects {

    private SafeRedirects() {
    }

    public static String toRefererOr(HttpServletRequest request, String fallbackPath) {
        String referer = request.getHeader("Referer");
        if (referer == null || referer.isBlank()) {
            return "redirect:" + fallbackPath;
        }
        try {
            java.net.URI uri = java.net.URI.create(referer);
            String host = uri.getHost();
            String requestHost = request.getServerName();
            if (host != null && requestHost != null && !host.equalsIgnoreCase(requestHost)) {
                return "redirect:" + fallbackPath;
            }
        } catch (Exception ignored) {
            return "redirect:" + fallbackPath;
        }
        return "redirect:" + referer;
    }
}
