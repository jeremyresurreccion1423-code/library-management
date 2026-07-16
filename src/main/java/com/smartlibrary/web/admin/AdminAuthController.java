package com.smartlibrary.web.admin;

import com.smartlibrary.model.UserRole;
import com.smartlibrary.security.LoginPortalPaths;
import com.smartlibrary.security.LibraryUserDetails;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/admin")
public class AdminAuthController {

    @GetMapping("/login")
    public String login(@AuthenticationPrincipal LibraryUserDetails user,
                        HttpSession session,
                        @RequestParam(required = false) String logout,
                        @RequestParam(required = false) String error,
                        Model model) {
        if (user != null && logout == null) {
            UserRole role = user.getUser().getRole();
            if (role == UserRole.ADMIN) {
                return "redirect:/admin";
            }
            if (role == UserRole.SUPER_ADMIN) {
                return "redirect:/super-admin";
            }
            model.addAttribute("infoMessage",
                    "You are signed in as a student. Enter admin credentials below to continue.");
        }
        if ("session".equals(error)) {
            model.addAttribute("errorMessage", "Your session has expired. Please log in again.");
        }
        Object authError = session.getAttribute("AUTH_ERROR");
        if (authError instanceof String errorMessage && !errorMessage.isBlank()) {
            model.addAttribute("errorMessage", errorMessage);
            session.removeAttribute("AUTH_ERROR");
        } else if (error != null) {
            model.addAttribute("errorMessage", "Incorrect username or password.");
        }
        if (logout != null) {
            model.addAttribute("success", "You have been logged out successfully.");
        }
        session.setAttribute(LoginPortalPaths.SESSION_PORTAL, "admin");
        return "admin/login";
    }
}

