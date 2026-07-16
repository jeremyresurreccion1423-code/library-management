package com.smartlibrary.web.superadmin;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * Keeps Super Admin users inside the /superadmin module — never on /admin/* pages.
 */
@Component
@Order(50)
public class SuperAdminRedirectFilter extends OncePerRequestFilter {

    private static final Map<String, String> ADMIN_TO_SUPERADMIN = Map.ofEntries(
            Map.entry("/admin", "/superadmin/library/dashboard"),
            Map.entry("/admin/", "/superadmin/library/dashboard"),
            Map.entry("/admin/categories", "/superadmin/library/categories"),
            Map.entry("/admin/authors", "/superadmin/library/authors"),
            Map.entry("/admin/books", "/superadmin/library/books"),
            Map.entry("/admin/scan-issue", "/superadmin/library/scan-issue"),
            Map.entry("/admin/issues", "/superadmin/library/issues"),
            Map.entry("/admin/issues/reservations", "/superadmin/library/reservations"),
            Map.entry("/admin/students", "/superadmin/library/students"),
            Map.entry("/admin/analytics", "/superadmin/library/analytics"),
            Map.entry("/admin/admins", "/superadmin/library/admins"),
            Map.entry("/admin/admins/new", "/superadmin/library/admins/new"),
            Map.entry("/admin/profile", "/superadmin/profile"),
            Map.entry("/admin/account/password", "/superadmin/account/password")
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                      HttpServletResponse response,
                                      FilterChain filterChain) throws ServletException, IOException {
        if ("GET".equalsIgnoreCase(request.getMethod())
                && request.getRequestURI() != null
                && request.getRequestURI().startsWith("/admin")
                && isSuperAdmin()) {
            String target = resolveTarget(request.getRequestURI());
            if (target != null) {
                String qs = request.getQueryString();
                response.sendRedirect(target + (qs != null && !qs.isBlank() ? "?" + qs : ""));
                return;
            }
            if (request.getRequestURI().startsWith("/admin/books/")
                    && request.getRequestURI().endsWith("/edit")) {
                response.sendRedirect("/superadmin/library" + request.getRequestURI().substring("/admin".length()));
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private static String resolveTarget(String uri) {
        if (ADMIN_TO_SUPERADMIN.containsKey(uri)) {
            return ADMIN_TO_SUPERADMIN.get(uri);
        }
        return null;
    }

    private static boolean isSuperAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()));
    }
}
