package com.smartlibrary.security;

import com.smartlibrary.entity.User;
import com.smartlibrary.repository.UserRepository;
import com.smartlibrary.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

@Component
public class AuditLogoutHandler implements LogoutHandler {

    private final UserRepository userRepository;
    private final AuditService auditService;

    public AuditLogoutHandler(UserRepository userRepository, AuditService auditService) {
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return;
        }
        User user = null;
        if (authentication.getPrincipal() instanceof LibraryUserDetails details) {
            user = details.getUser();
        } else {
            user = userRepository.findByUsername(authentication.getName()).orElse(null);
        }
        auditService.log(user, "LOGOUT", "User", user != null ? user.getId() : null,
                "User logged out from " + AuditService.clientIp(request));
    }
}
