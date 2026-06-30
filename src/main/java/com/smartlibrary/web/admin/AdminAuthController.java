package com.smartlibrary.web.admin;

import com.smartlibrary.model.UserRole;
import com.smartlibrary.security.LibraryUserDetails;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
public class AdminAuthController {

    @GetMapping("/login")
    public String login(@AuthenticationPrincipal LibraryUserDetails user, HttpSession session, Model model) {
        if (user != null) {
            if (user.getUser().getRole() == UserRole.ADMIN) {
                return "redirect:/admin";
            }
            return "redirect:/student";
        }
        Object authError = session.getAttribute("AUTH_ERROR");
        if (authError instanceof String errorMessage && !errorMessage.isBlank()) {
            model.addAttribute("errorMessage", errorMessage);
            session.removeAttribute("AUTH_ERROR");
        }
        return "admin/login";
    }
}

