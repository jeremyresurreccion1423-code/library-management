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
        String portalKey = LoginPortalPaths.portalKey(request);
        String loginPath = LoginPortalPaths.resolveLoginPath(request);

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

        try {
            accountLockoutService.onFailedLogin(username);
        } catch (RuntimeException ex) {
            // Never block the login error redirect if lockout bookkeeping fails.
        }

        request.getSession().setAttribute("AUTH_ERROR", LoginPortalPaths.failureMessage(portalKey));
        getRedirectStrategy().sendRedirect(request, response, loginPath + "?error=true");
    }
}
