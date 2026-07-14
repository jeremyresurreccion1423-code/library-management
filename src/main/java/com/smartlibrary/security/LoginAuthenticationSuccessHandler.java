package com.smartlibrary.security;

import com.smartlibrary.config.LibraryProperties;
import com.smartlibrary.entity.User;
import com.smartlibrary.model.UserRole;
import com.smartlibrary.repository.UserRepository;
import com.smartlibrary.service.AccountLockoutService;
import com.smartlibrary.service.AuditService;
import com.smartlibrary.service.LoginNotificationService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class LoginAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final AccountLockoutService accountLockoutService;
    private final AuditService auditService;
    private final LoginNotificationService loginNotificationService;
    private final LibraryProperties libraryProperties;

    public LoginAuthenticationSuccessHandler(
            UserRepository userRepository,
            AccountLockoutService accountLockoutService,
            AuditService auditService,
            LoginNotificationService loginNotificationService,
            LibraryProperties libraryProperties) {
        this.userRepository = userRepository;
        this.accountLockoutService = accountLockoutService;
        this.auditService = auditService;
        this.loginNotificationService = loginNotificationService;
        this.libraryProperties = libraryProperties;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        String uri = request.getRequestURI() != null ? request.getRequestURI() : "";
        boolean isSuperAdminPortal = uri.startsWith("/super-admin");
        boolean isAdminPortal = uri.startsWith("/admin");

        User user = null;
        if (authentication.getPrincipal() instanceof LibraryUserDetails details) {
            user = details.getUser();
        } else {
            user = userRepository.findByUsername(authentication.getName()).orElse(null);
        }

        if (user != null) {
            accountLockoutService.onSuccessfulLogin(user);
            String portalLabel = isSuperAdminPortal ? "Super Admin portal login"
                    : isAdminPortal ? "Admin portal login"
                    : "User login";
            auditService.log(user, "LOGIN", "User", user.getId(),
                    portalLabel + " from " + AuditService.clientIp(request));
            if (user.getRole() == UserRole.ADMIN || user.getRole() == UserRole.SUPER_ADMIN) {
                loginNotificationService.notifyLogin(user, request);
            }
        }

        if (isSuperAdminPortal) {
            if (user == null || user.getRole() != UserRole.SUPER_ADMIN) {
                new SecurityContextLogoutHandler().logout(request, response, authentication);
                request.getSession().setAttribute("AUTH_ERROR", "Invalid Super Admin credentials.");
                response.sendRedirect("/super-admin/login");
                return;
            }
            response.sendRedirect("/super-admin");
            return;
        }

        if (isAdminPortal) {
            if (user == null || user.getRole() != UserRole.ADMIN) {
                new SecurityContextLogoutHandler().logout(request, response, authentication);
                request.getSession().setAttribute("AUTH_ERROR", "Invalid username or password.");
                response.sendRedirect("/admin/login");
                return;
            }
            response.sendRedirect("/admin");
            return;
        }

        if (user != null && (user.getRole() == UserRole.ADMIN || user.getRole() == UserRole.SUPER_ADMIN)) {
            new SecurityContextLogoutHandler().logout(request, response, authentication);
            request.getSession().setAttribute("AUTH_ERROR", "Invalid username or password.");
            response.sendRedirect("/login");
            return;
        }
        if (user != null && user.getRole() == UserRole.TEACHER) {
            new SecurityContextLogoutHandler().logout(request, response, authentication);
            request.getSession().setAttribute("AUTH_ERROR",
                    "Teacher accounts must sign in via the Attendance System: "
                            + libraryProperties.getAttendanceLoginUrl());
            response.sendRedirect("/login");
            return;
        }
        response.sendRedirect("/redirect-home");
    }
}
