package com.smartlibrary.web.student;

import com.smartlibrary.security.LibraryUserDetails;
import com.smartlibrary.service.UserAccountService;
import com.smartlibrary.web.SafeRedirects;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/student/account")
public class StudentPasswordController {

    private static final String SESSION_PWD_STEP = "studentPwdChangeStep";
    private static final String SESSION_PWD_OTP_VERIFIED = "studentPwdOtpVerified";

    private final UserAccountService userAccountService;

    public StudentPasswordController(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    @GetMapping("/password")
    public String form(Model model, @AuthenticationPrincipal LibraryUserDetails user, HttpSession session) {
        String accountEmail = user.getUser().getEmail();
        model.addAttribute("email", accountEmail);
        model.addAttribute("deliveryEmail", userAccountService.resolveOtpDeliveryEmail(accountEmail));
        model.addAttribute("step", currentStep(session));
        return "student/change-password";
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
            String accountEmail = user.getUser().getEmail();

            // Inline profile-widget flow: direct change with confirm
            if (confirmPassword != null && currentPassword != null && !"verified".equals(currentPassword)) {
                if (!newPassword.equals(confirmPassword)) {
                    ra.addFlashAttribute("error", "New password and confirmation do not match.");
                    return SafeRedirects.toRefererOr(request, "/student/account/password");
                }
                userAccountService.changePassword(user.getUsername(), currentPassword, newPassword);
                clearPasswordChangeSession(session);
                ra.addFlashAttribute("success", "Password updated successfully");
                return SafeRedirects.toRefererOr(request, "/student");
            }

            int step = currentStep(session);

            if ("send-otp".equals(action) || (step == 1 && otp == null && newPassword == null)) {
                userAccountService.generateAndSendOtp(accountEmail);
                session.setAttribute(SESSION_PWD_STEP, 2);
                ra.addFlashAttribute("message", "OTP sent to your email. Please enter the code to continue.");
                return "redirect:/student/account/password";
            }

            if (step == 2) {
                if (otp == null || otp.isBlank()) {
                    ra.addFlashAttribute("error", "Please enter the OTP code.");
                    return "redirect:/student/account/password";
                }
                userAccountService.verifyOtp(accountEmail, otp);
                session.setAttribute(SESSION_PWD_STEP, 3);
                session.setAttribute(SESSION_PWD_OTP_VERIFIED, Boolean.TRUE);
                ra.addFlashAttribute("message", "OTP verified. Create your new password below.");
                return "redirect:/student/account/password";
            }

            if (step == 3) {
                if (!Boolean.TRUE.equals(session.getAttribute(SESSION_PWD_OTP_VERIFIED))) {
                    throw new IllegalStateException("Please verify OTP before setting a new password.");
                }
                if (newPassword == null || newPassword.isBlank()) {
                    ra.addFlashAttribute("error", "New password is required.");
                    return "redirect:/student/account/password";
                }
                if (confirmPassword != null && !newPassword.equals(confirmPassword)) {
                    ra.addFlashAttribute("error", "New password and confirmation do not match.");
                    return "redirect:/student/account/password";
                }
                userAccountService.changePasswordWithoutOld(user.getUsername(), newPassword);
                clearPasswordChangeSession(session);
                ra.addFlashAttribute("success", "Password updated successfully");
                return "redirect:/student";
            }

            ra.addFlashAttribute("error", "Invalid password change request.");
            return "redirect:/student/account/password";
        } catch (Exception e) {
            ra.addFlashAttribute("error", friendlyError(e));
            if (currentStep(session) >= 2) {
                ra.addFlashAttribute("step", currentStep(session));
            }
            return SafeRedirects.toRefererOr(request, "/student/account/password");
        }
    }

    @PostMapping("/password/cancel")
    public String cancel(HttpSession session) {
        clearPasswordChangeSession(session);
        return "redirect:/student/account/password";
    }

    @PostMapping("/password/send-otp")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> sendOtp(@AuthenticationPrincipal LibraryUserDetails user) {
        Map<String, Object> response = new HashMap<>();
        try {
            userAccountService.generateAndSendOtp(user.getUser().getEmail());
            response.put("success", true);
            response.put("message", "OTP sent to your email");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", friendlyError(e));
            return ResponseEntity.ok(response);
        }
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
