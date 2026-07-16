package com.smartlibrary.web.superadmin;

import com.smartlibrary.security.LibraryUserDetails;
import com.smartlibrary.service.UserAccountService;
import com.smartlibrary.web.SafeRedirects;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/superadmin/account")
public class SuperAdminPasswordController {

    private static final String SESSION_PWD_STEP = "superAdminPwdChangeStep";
    private static final String SESSION_PWD_OTP_VERIFIED = "superAdminPwdOtpVerified";
    private static final String DEFAULT_ADMIN_EMAIL = "resurreccionjeremy9@gmail.com";

    private final UserAccountService userAccountService;

    public SuperAdminPasswordController(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    @GetMapping("/password")
    public String form(@AuthenticationPrincipal LibraryUserDetails user, Model model, HttpSession session) {
        String accountEmail = resolveAccountEmail(user);

        model.addAttribute("email", accountEmail);
        model.addAttribute("deliveryEmail", userAccountService.resolveOtpDeliveryEmail(accountEmail));
        model.addAttribute("username", user.getUsername());
        model.addAttribute("step", currentStep(session));
        model.addAttribute("dashboardPath", "/super-admin");
        model.addAttribute("profilePath", "/superadmin/profile");
        return "superadmin/account-password";
    }

    @PostMapping("/password")
    public String submit(
            @AuthenticationPrincipal LibraryUserDetails user,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String currentPassword,
            @RequestParam(required = false) String newPassword,
            @RequestParam(required = false) String confirmPassword,
            @RequestParam(required = false) String otp,
            RedirectAttributes ra,
            HttpServletRequest request,
            HttpSession session) {
        try {
            String accountEmail = resolveAccountEmail(user);

            // Legacy inline profile-widget flow
            if (confirmPassword != null && currentPassword != null && !"verified".equals(currentPassword)) {
                if (!newPassword.equals(confirmPassword)) {
                    ra.addFlashAttribute("error", "New password and confirmation do not match.");
                    return SafeRedirects.toRefererOr(request, "/superadmin/account/password");
                }
                userAccountService.changePassword(user.getUsername(), currentPassword, newPassword);
                clearPasswordChangeSession(session);
                ra.addFlashAttribute("success", "Password updated successfully.");
                return SafeRedirects.toRefererOr(request, "/super-admin");
            }

            int step = currentStep(session);

            if ("send-otp".equals(action) || (step == 1 && otp == null && newPassword == null)) {
                userAccountService.generateAndSendOtp(accountEmail);
                session.setAttribute(SESSION_PWD_STEP, 2);
                ra.addFlashAttribute("message", "OTP sent to your email. Please enter the code to continue.");
                return "redirect:/superadmin/account/password";
            }

            if (step == 2) {
                if (otp == null || otp.isBlank()) {
                    ra.addFlashAttribute("error", "Please enter the OTP code.");
                    return "redirect:/superadmin/account/password";
                }
                userAccountService.verifyOtp(accountEmail, otp);
                session.setAttribute(SESSION_PWD_STEP, 3);
                session.setAttribute(SESSION_PWD_OTP_VERIFIED, Boolean.TRUE);
                ra.addFlashAttribute("message", "OTP verified. Create your new password below.");
                return "redirect:/superadmin/account/password";
            }

            if (step == 3) {
                if (!Boolean.TRUE.equals(session.getAttribute(SESSION_PWD_OTP_VERIFIED))) {
                    throw new IllegalStateException("Please verify OTP before setting a new password.");
                }
                if (newPassword == null || newPassword.isBlank()) {
                    ra.addFlashAttribute("error", "New password is required.");
                    return "redirect:/superadmin/account/password";
                }
                if (confirmPassword != null && !newPassword.equals(confirmPassword)) {
                    ra.addFlashAttribute("error", "New password and confirmation do not match.");
                    return "redirect:/superadmin/account/password";
                }
                userAccountService.changePasswordWithoutOld(user.getUsername(), newPassword);
                clearPasswordChangeSession(session);
                ra.addFlashAttribute("success", "Password updated successfully.");
                return "redirect:/super-admin";
            }

            ra.addFlashAttribute("error", "Invalid password change request.");
            return "redirect:/superadmin/account/password";
        } catch (Exception e) {
            ra.addFlashAttribute("error", friendlyError(e));
            return SafeRedirects.toRefererOr(request, "/superadmin/account/password");
        }
    }

    @PostMapping("/password/cancel")
    public String cancel(HttpSession session) {
        clearPasswordChangeSession(session);
        return "redirect:/superadmin/account/password";
    }

    private String resolveAccountEmail(LibraryUserDetails user) {
        String email = user.getUser().getEmail();
        if (email == null || email.isBlank()) {
            return DEFAULT_ADMIN_EMAIL;
        }
        return email;
    }

    private static int currentStep(HttpSession session) {
        Object step = session.getAttribute(SESSION_PWD_STEP);
        return step instanceof Integer ? (Integer) step : 1;
    }

    private static void clearPasswordChangeSession(HttpSession session) {
        session.removeAttribute(SESSION_PWD_STEP);
        session.removeAttribute(SESSION_PWD_OTP_VERIFIED);
    }

    private static String friendlyError(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return "Unable to update password. Please try again.";
        }
        if (message.contains("Could not commit JPA transaction")) {
            return "Unable to save password change. Please try again or contact support.";
        }
        return message;
    }
}
