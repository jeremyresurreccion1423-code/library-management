package com.smartlibrary.security;

import com.smartlibrary.service.AccountLockoutService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class LoginAuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final AccountLockoutService accountLockoutService;

    public LoginAuthenticationFailureHandler(AccountLockoutService accountLockoutService) {
        this.accountLockoutService = accountLockoutService;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        String username = request.getParameter("username");
        String servletPath = request.getServletPath();
        if (servletPath == null || servletPath.isBlank()) {
            servletPath = request.getRequestURI() != null ? request.getRequestURI() : "";
        }

        boolean isSuperAdminPortal = servletPath.startsWith("/super-admin");
        boolean isAdminPortal = servletPath.startsWith("/admin");
        String loginPath = isSuperAdminPortal ? "/super-admin/login"
                : isAdminPortal ? "/admin/login"
                : "/login";

        if (exception instanceof LockedException) {
            request.getSession().setAttribute("AUTH_ERROR",
                    "Account locked due to too many failed login attempts. Try again in "
                            + AccountLockoutService.LOCK_MINUTES + " minutes or contact an administrator.");
            getRedirectStrategy().sendRedirect(request, response, loginPath + "?error=true");
            return;
        }
        if (exception instanceof DisabledException) {
            request.getSession().setAttribute("AUTH_ERROR", "This account has been disabled.");
            getRedirectStrategy().sendRedirect(request, response, loginPath + "?error=true");
            return;
        }

        accountLockoutService.onFailedLogin(username);

        String message = isSuperAdminPortal
                ? "Invalid Super Admin credentials."
                : "Incorrect username or password.";
        request.getSession().setAttribute("AUTH_ERROR", message);
        getRedirectStrategy().sendRedirect(request, response, loginPath + "?error=true");
    }
}
