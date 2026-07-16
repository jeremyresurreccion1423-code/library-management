package com.smartlibrary.web.superadmin;

import com.smartlibrary.model.UserRole;
import com.smartlibrary.repository.UserRepository;
import com.smartlibrary.security.SsoTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

/** Super Admin SSO handoff (inbound token login). */
@Controller
public class SuperAdminSsoController {

    private final SsoTokenService ssoTokenService;
    private final UserRepository userRepository;
    private final UserDetailsService userDetailsService;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public SuperAdminSsoController(
            SsoTokenService ssoTokenService,
            UserRepository userRepository,
            UserDetailsService userDetailsService) {
        this.ssoTokenService = ssoTokenService;
        this.userRepository = userRepository;
        this.userDetailsService = userDetailsService;
    }

    /** Inbound: consume a handoff token and sign the Super Admin into this app. */
    @GetMapping("/super-admin/sso")
    public String receiveSso(@RequestParam String token,
                             @RequestParam(defaultValue = "/super-admin") String next,
                             HttpServletRequest request,
                             HttpServletResponse response) {
        Optional<String> usernameOpt = ssoTokenService.validateToken(token);
        if (usernameOpt.isEmpty()) {
            return "redirect:/super-admin/login?error=true";
        }
        String username = usernameOpt.get();
        var userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty() || userOpt.get().getRole() != UserRole.SUPER_ADMIN || !userOpt.get().isEnabled()) {
            return "redirect:/super-admin/login?error=true";
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        String safeNext = isSafeLocalPath(next) ? next : "/super-admin";
        return "redirect:" + safeNext;
    }

    /** Only allow same-app relative paths as SSO redirect targets (blocks open-redirect to external hosts). */
    private boolean isSafeLocalPath(String path) {
        return path != null && path.startsWith("/") && !path.startsWith("//");
    }
}
