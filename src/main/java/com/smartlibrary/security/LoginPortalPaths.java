package com.smartlibrary.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * Resolves which login page a failed/successful authentication belongs to.
 * Uses explicit processing URLs because multi-chain Spring Security can route
 * POST /admin/login to the wrong filter chain when page and processing share one path.
 */
public final class LoginPortalPaths {

    public static final String SESSION_PORTAL = "LOGIN_PORTAL";
    public static final String FORM_PORTAL = "loginPortal";

    public static final String STUDENT_LOGIN = "/login";
    public static final String ADMIN_LOGIN = "/admin/login";
    public static final String ADMIN_PROCESS = "/admin/login/process";
    public static final String SUPER_ADMIN_LOGIN = "/super-admin/login";
    public static final String SUPER_ADMIN_PROCESS = "/super-admin/login/process";

    private LoginPortalPaths() {
    }

    public static String resolveLoginPath(HttpServletRequest request) {
        String portal = portalKey(request);
        return switch (portal) {
            case "super-admin" -> SUPER_ADMIN_LOGIN;
            case "admin" -> ADMIN_LOGIN;
            default -> STUDENT_LOGIN;
        };
    }

    public static String portalKey(HttpServletRequest request) {
        String path = normalizedPath(request);
        if (path.startsWith(LoginPortalPaths.SUPER_ADMIN_PROCESS)
                || path.startsWith("/super-admin/login")) {
            return "super-admin";
        }
        if (path.startsWith(LoginPortalPaths.ADMIN_PROCESS)
                || path.startsWith("/admin/login")) {
            return "admin";
        }

        String formPortal = request.getParameter(FORM_PORTAL);
        if ("super-admin".equals(formPortal)) {
            return "super-admin";
        }
        if ("admin".equals(formPortal)) {
            return "admin";
        }

        HttpSession session = request.getSession(false);
        if (session != null) {
            Object sessionPortal = session.getAttribute(SESSION_PORTAL);
            if ("super-admin".equals(sessionPortal)) {
                return "super-admin";
            }
            if ("admin".equals(sessionPortal)) {
                return "admin";
            }
        }

        return "student";
    }

    public static String failureMessage(String portalKey) {
        return "super-admin".equals(portalKey)
                ? "Invalid Super Admin credentials."
                : "Incorrect username or password.";
    }

    private static String normalizedPath(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        if (servletPath != null && !servletPath.isBlank()) {
            return servletPath;
        }
        String uri = request.getRequestURI();
        return uri != null ? uri : "";
    }
}
