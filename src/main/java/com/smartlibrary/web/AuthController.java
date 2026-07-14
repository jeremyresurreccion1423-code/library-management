package com.smartlibrary.web;

import com.smartlibrary.config.LibraryProperties;
import com.smartlibrary.security.LibraryUserDetails;
import jakarta.servlet.http.HttpSession;
import com.smartlibrary.service.UserAccountService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

@Controller
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    private static final String EMAIL_REGEX = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";

    private final UserAccountService userAccountService;
    private final LibraryProperties libraryProperties;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    public AuthController(UserAccountService userAccountService, LibraryProperties libraryProperties) {
        this.userAccountService = userAccountService;
        this.libraryProperties = libraryProperties;
    }

    @GetMapping("/login")
    public String login(@AuthenticationPrincipal LibraryUserDetails user,
                        HttpSession session,
                        @RequestParam(required = false) String logout,
                        @RequestParam(required = false) String error,
                        Model model) {
        if (user != null) {
            return "redirect:/redirect-home";
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
        model.addAttribute("attendanceLoginUrl", libraryProperties.getAttendanceLoginUrl());
        return "login";
    }

    @GetMapping("/register")
    public String registerForm() {
        return "register";
    }

    @PostMapping("/register/send-otp")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> sendOtp(@RequestParam String email) {
        Map<String, Object> response = new HashMap<>();
        try {
            if (email == null || email.trim().isBlank() || !email.matches(EMAIL_REGEX)) {
                response.put("success", false);
                response.put("message", "Please enter a valid email address");
                logger.warn("Invalid email format attempted: {}", email);
                return ResponseEntity.ok(response);
            }

            userAccountService.deleteUnverifiedAccount(email);

            if (userAccountService.emailExists(email)) {
                response.put("success", false);
                response.put("message", "An account with that email already exists");
                logger.info("Registration attempt with existing email: {}", email);
                return ResponseEntity.ok(response);
            }

            userAccountService.generateAndSendOtp(email);
            response.put("success", true);
            response.put("message", "OTP sent to " + email + ". Valid for 5 minutes.");
            logger.info("OTP sent to email: {}", email);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to send OTP: " + e.getMessage());
            logger.error("Error sending OTP to {}: {}", email, e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    @PostMapping("/register")
    public String register(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam String confirmPassword,
            @RequestParam String email,
            @RequestParam(required = false) String firstName,
            @RequestParam(required = false) String lastName,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String course,
            @RequestParam(required = false) String otp,
            RedirectAttributes ra) {
        try {
            if (!password.equals(confirmPassword)) {
                throw new IllegalArgumentException("Password and confirm password do not match");
            }

            String resolvedFirstName = firstName == null ? "" : firstName.trim();
            String resolvedLastName = lastName == null ? "" : lastName.trim();
            String fullName = (resolvedFirstName + " " + resolvedLastName).trim();

            if (fullName.isBlank()) {
                throw new IllegalArgumentException("Name and surname are required");
            }

            if (otp != null && !otp.isBlank()) {
                userAccountService.verifyRegistrationOtp(email, otp);
                String studentId = userAccountService.findStudentIdByEmail(email);
                ra.addFlashAttribute("success",
                        "Registration successful! Your Student ID is "
                                + (studentId != null ? studentId : "N/A")
                                + ". Your account is now verified. You may log in now.");
                logger.info("User registered successfully: {} with student ID: {}", email, studentId);
                return "redirect:/login";
            }

            var profile = userAccountService.registerAndSendOtp(username, password, email, fullName, phone, course);
            userAccountService.updateStudentDemographicsByEmail(email, resolvedFirstName, resolvedLastName);
            ra.addFlashAttribute("otpRequired", true);
            ra.addFlashAttribute("studentId", profile.getStudentId());
            ra.addFlashAttribute("email", email);
            ra.addFlashAttribute("username", username);
            ra.addFlashAttribute("password", password);
            ra.addFlashAttribute("phone", phone);
            ra.addFlashAttribute("course", course);
            ra.addFlashAttribute("firstName", resolvedFirstName);
            ra.addFlashAttribute("lastName", resolvedLastName);
            String lastOtp = userAccountService.getLastGeneratedOtp(email);
            if (lastOtp != null && (mailHost == null || mailHost.isBlank() || mailPassword == null || mailPassword.isBlank())) {
                ra.addFlashAttribute("devOtp", lastOtp);
            }
            if (lastOtp == null) {
                ra.addFlashAttribute("error",
                        "Account created, but the OTP email could not be sent. Click Resend OTP or check mail configuration.");
            } else {
                ra.addFlashAttribute("message",
                        "Account created successfully! Enter the OTP code sent to your email.");
            }
            logger.info("New registration initiated for email: {} (otpStored={})", email, lastOtp != null);
            return "redirect:/register";
        } catch (IllegalArgumentException e) {
            String errorMsg = e.getMessage();
            Map<String, String> fieldErrors = new HashMap<>();

            if (errorMsg.toLowerCase().contains("password") && errorMsg.toLowerCase().contains("match")) {
                fieldErrors.put("confirmPassword", errorMsg);
            } else if (errorMsg.toLowerCase().contains("name") && errorMsg.toLowerCase().contains("required")) {
                fieldErrors.put("firstName", errorMsg);
                fieldErrors.put("lastName", errorMsg);
            } else if (errorMsg.toLowerCase().contains("email")) {
                fieldErrors.put("email", errorMsg);
            } else if (errorMsg.toLowerCase().contains("username")) {
                fieldErrors.put("username", errorMsg);
            } else {
                fieldErrors.put("general", errorMsg);
            }

            ra.addFlashAttribute("fieldErrors", fieldErrors);
            ra.addFlashAttribute("username", username);
            ra.addFlashAttribute("email", email);
            ra.addFlashAttribute("firstName", firstName);
            ra.addFlashAttribute("lastName", lastName);
            ra.addFlashAttribute("phone", phone);
            ra.addFlashAttribute("course", course);
            ra.addFlashAttribute("password", password);
            ra.addFlashAttribute("confirmPassword", confirmPassword);
            logger.warn("Registration validation failed: {}", e.getMessage());
            return "redirect:/register";
        }
    }

    @GetMapping("/forgot-password")
    public String forgotForm() {
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String forgotSubmit(@RequestParam String emailOrUsername,
            @RequestParam(required = false) String otp,
            @RequestParam(required = false) String newPassword,
            RedirectAttributes ra) {
        try {
            if (otp != null && !otp.isBlank()) {
                userAccountService.resetPasswordWithOtp(emailOrUsername, otp, newPassword);
                ra.addFlashAttribute("success", "Password reset successful! Please log in with your new password.");
                logger.info("Password reset completed for user: {}", emailOrUsername);
                return "redirect:/login";
            }

            userAccountService.requestPasswordResetOtp(emailOrUsername);
            ra.addFlashAttribute("otpRequired", true);
            ra.addFlashAttribute("emailOrUsername", emailOrUsername);
            ra.addFlashAttribute("message", "OTP sent to your email. Please enter the code to reset your password.");
            logger.info("Password reset OTP sent to: {}", emailOrUsername);
            return "redirect:/forgot-password";
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
            logger.warn("Password reset request failed: {}", e.getMessage());
            return "redirect:/forgot-password";
        }
    }
}
