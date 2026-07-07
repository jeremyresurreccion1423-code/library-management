package com.smartlibrary.web.admin;

import com.smartlibrary.model.UserRole;
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
        if (user != null) {
            if (user.getUser().getRole() == UserRole.ADMIN) {
                return "redirect:/admin";
            }
            return "redirect:/student";
        }
        if ("session".equals(error)) {
            model.addAttribute("errorMessage", "Your session has expired. Please log in again.");
        }
        Object authError = session.getAttribute("AUTH_ERROR");
        if (authError instanceof String errorMessage && !errorMessage.isBlank()) {
            model.addAttribute("errorMessage", errorMessage);
            session.removeAttribute("AUTH_ERROR");
        }
        if (logout != null) {
            model.addAttribute("success", "You have been logged out successfully.");
        }
        return "admin/login";
    }
}

