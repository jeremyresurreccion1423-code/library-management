package com.smartlibrary.web.student;

import com.smartlibrary.security.LibraryUserDetails;
import com.smartlibrary.service.UserAccountService;
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

    private final UserAccountService userAccountService;

    public StudentPasswordController(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    @GetMapping("/password")
    public String form(Model model, @AuthenticationPrincipal LibraryUserDetails user) {
        model.addAttribute("email", user.getUser().getEmail());
        return "student/change-password";
    }

    @PostMapping("/password")
    public String submit(
            @AuthenticationPrincipal LibraryUserDetails user,
            @RequestParam String currentPassword,
            @RequestParam String newPassword,
            @RequestParam(required = false) String otp,
            RedirectAttributes ra) {
        try {
            if (otp != null && !otp.isBlank()) {
                String userEmail = user.getUser().getEmail();
                userAccountService.verifyOtp(userEmail, otp);
                userAccountService.changePasswordWithoutOld(user.getUsername(), newPassword);
                ra.addFlashAttribute("success", "Password updated successfully");
                return "redirect:/student";
            }
            String userEmail = user.getUser().getEmail();
            userAccountService.validateCurrentPassword(user.getUsername(), currentPassword);
            userAccountService.generateAndSendOtp(userEmail);
            ra.addFlashAttribute("otpRequired", true);
            ra.addFlashAttribute("email", userEmail);
            ra.addFlashAttribute("newPassword", newPassword);
            ra.addFlashAttribute("message", "OTP sent to your email. Please enter the code to confirm password change.");
            return "redirect:/student/account/password";
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/student/account/password";
        }
    }

    @PostMapping("/password/send-otp")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> sendOtp(@AuthenticationPrincipal LibraryUserDetails user) {
        Map<String, Object> response = new HashMap<>();
        try {
            String userEmail = user.getUser().getEmail();
            userAccountService.generateAndSendOtp(userEmail);
            response.put("success", true);
            response.put("message", "OTP sent to your email");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }
}
