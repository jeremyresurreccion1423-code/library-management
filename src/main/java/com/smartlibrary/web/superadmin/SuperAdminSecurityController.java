package com.smartlibrary.web.superadmin;

import com.smartlibrary.entity.User;
import com.smartlibrary.repository.UserRepository;
import com.smartlibrary.security.LibraryUserDetails;
import com.smartlibrary.service.AccountLockoutService;
import com.smartlibrary.service.SecurityDashboardService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class SuperAdminSecurityController {

    private final SecurityDashboardService securityDashboardService;
    private final AccountLockoutService accountLockoutService;
    private final UserRepository userRepository;

    public SuperAdminSecurityController(
            SecurityDashboardService securityDashboardService,
            AccountLockoutService accountLockoutService,
            UserRepository userRepository) {
        this.securityDashboardService = securityDashboardService;
        this.accountLockoutService = accountLockoutService;
        this.userRepository = userRepository;
    }

    @GetMapping("/super-admin/security")
    public String securityCenter(Model model) {
        model.addAttribute("sec", securityDashboardService.getDashboard());
        return "super-admin/security";
    }

    @PostMapping("/super-admin/security/unlock/{userId}")
    public String unlockAccount(@PathVariable Long userId,
                                @AuthenticationPrincipal LibraryUserDetails principal,
                                RedirectAttributes redirect) {
        User actor = principal != null ? principal.getUser() : null;
        userRepository.findById(userId).ifPresentOrElse(user -> {
            accountLockoutService.unlock(user, actor);
            redirect.addFlashAttribute("success", "Unlocked account: " + user.getUsername());
        }, () -> redirect.addFlashAttribute("error", "User not found"));
        return "redirect:/super-admin/security";
    }
}
