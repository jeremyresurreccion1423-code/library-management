package com.smartlibrary.web.admin;

import com.smartlibrary.security.LibraryUserDetails;
import com.smartlibrary.service.UserAccountService;
import com.smartlibrary.web.SafeRedirects;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/account")
public class AdminPasswordController {

    private final UserAccountService userAccountService;

    public AdminPasswordController(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    @GetMapping("/password")
    public String form(@AuthenticationPrincipal LibraryUserDetails user, Model model) {
        model.addAttribute("email", user.getUser().getEmail());
        model.addAttribute("username", user.getUsername());
        boolean superAdmin = user.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));
        model.addAttribute("dashboardPath", superAdmin ? "/super-admin" : "/admin");
        model.addAttribute("profilePath", "/admin/profile");
        return "admin/change-password";
    }

    @PostMapping("/password")
    public String submit(
            @AuthenticationPrincipal LibraryUserDetails user,
            @RequestParam String currentPassword,
            @RequestParam String newPassword,
            @RequestParam String confirmPassword,
            RedirectAttributes ra,
            HttpServletRequest request) {
        try {
            if (!newPassword.equals(confirmPassword)) {
                ra.addFlashAttribute("error", "New password and confirmation do not match.");
                return SafeRedirects.toRefererOr(request, "/admin/account/password");
            }
            userAccountService.changePassword(user.getUsername(), currentPassword, newPassword);
            ra.addFlashAttribute("success", "Password updated successfully.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", friendlyError(e));
        }
        return SafeRedirects.toRefererOr(request, "/admin/account/password");
    }

    private static String friendlyError(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return "Unable to update password. Please try again.";
        }
        if (message.contains("Could not commit JPA transaction")) {
            return "Unable to save password change. Please try again.";
        }
        return message;
    }
}
