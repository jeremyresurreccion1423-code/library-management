package com.smartlibrary.web;

import com.smartlibrary.model.UserRole;
import com.smartlibrary.security.LibraryUserDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private static final Logger logger = LoggerFactory.getLogger(HomeController.class);

    @GetMapping("/")
    public String home() {
        logger.debug("Root endpoint accessed, redirecting to login");
        return "redirect:/login";
    }

    @GetMapping("/redirect-home")
    public String redirectHome(@AuthenticationPrincipal LibraryUserDetails user) {
        if (user == null) {
            logger.warn("Redirect-home accessed without authentication, redirecting to login");
            return "redirect:/login";
        }
        
        String redirectPath = switch (user.getUser().getRole()) {
            case SUPER_ADMIN -> "/super-admin";
            case ADMIN -> "/admin";
            case STUDENT -> "/student";
            case TEACHER -> "/login";
        };
        logger.info("Authenticated user {} redirected to {}", user.getUsername(), redirectPath);
        
        return "redirect:" + redirectPath;
    }
}
