package com.smartlibrary.web.admin;

import com.smartlibrary.security.LibraryUserDetails;
import com.smartlibrary.service.UserAccountService;
import com.smartlibrary.web.SafeRedirects;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
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
    public String form() {
        return "admin/change-password";
    }

    @PostMapping("/password")
    public String submit(
            @AuthenticationPrincipal LibraryUserDetails user,
            @RequestParam String currentPassword,
            @RequestParam String newPassword,
            @RequestParam(required = false) String confirmPassword,
            RedirectAttributes ra,
            HttpServletRequest request) {
        try {
            if (confirmPassword != null && !newPassword.equals(confirmPassword)) {
                ra.addFlashAttribute("error", "New password and confirmation do not match.");
                return SafeRedirects.toRefererOr(request, "/admin/account/password");
            }
            userAccountService.changePassword(user.getUsername(), currentPassword, newPassword);
            ra.addFlashAttribute("success", "Password updated");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return SafeRedirects.toRefererOr(request, "/admin/account/password");
    }
}
