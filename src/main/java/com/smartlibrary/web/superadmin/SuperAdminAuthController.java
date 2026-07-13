package com.smartlibrary.web.superadmin;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Dedicated login page for the Super Admin role — kept completely separate from the
 * regular Admin/Student login so Super Admin authentication never mixes with
 * end-user flows.
 */
@Controller
public class SuperAdminAuthController {

    @GetMapping("/super-admin/login")
    public String login(HttpSession session,
                        @RequestParam(required = false) String logout,
                        @RequestParam(required = false) String error,
                        Model model) {
        Object authError = session.getAttribute("AUTH_ERROR");
        if (authError instanceof String errorMessage && !errorMessage.isBlank()) {
            model.addAttribute("errorMessage", errorMessage);
            session.removeAttribute("AUTH_ERROR");
        } else if (error != null) {
            model.addAttribute("errorMessage", "Invalid Super Admin credentials.");
        }
        if (logout != null) {
            model.addAttribute("success", "You have been logged out of the System Control Center.");
        }
        return "super-admin/login";
    }
}
