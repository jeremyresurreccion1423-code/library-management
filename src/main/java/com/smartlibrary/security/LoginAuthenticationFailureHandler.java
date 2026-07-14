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
        String uri = request.getRequestURI() != null ? request.getRequestURI() : "";
        String loginPath = uri.startsWith("/super-admin") ? "/super-admin/login"
                : uri.startsWith("/admin") ? "/admin/login"
                : "/login";

        if (exception instanceof LockedException) {
            request.getSession().setAttribute("AUTH_ERROR",
                    "Account locked due to too many failed login attempts. Try again in "
                            + AccountLockoutService.LOCK_MINUTES + " minutes or contact an administrator.");
            getRedirectStrategy().sendRedirect(request, response, loginPath);
            return;
        }
        if (exception instanceof DisabledException) {
            request.getSession().setAttribute("AUTH_ERROR", "This account has been disabled.");
            getRedirectStrategy().sendRedirect(request, response, loginPath);
            return;
        }

        accountLockoutService.onFailedLogin(username);

        String message = uri.startsWith("/super-admin")
                ? "Invalid Super Admin credentials."
                : "Invalid username or password.";
        request.getSession().setAttribute("AUTH_ERROR", message);
        getRedirectStrategy().sendRedirect(request, response, loginPath);
    }
}
