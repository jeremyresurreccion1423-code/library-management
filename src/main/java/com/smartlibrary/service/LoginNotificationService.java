package com.smartlibrary.service;

import com.smartlibrary.config.LibraryProperties;
import com.smartlibrary.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class LoginNotificationService {

    private static final Logger log = LoggerFactory.getLogger(LoginNotificationService.class);

    private final JavaMailSender mailSender;
    private final LibraryProperties libraryProperties;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    public LoginNotificationService(JavaMailSender mailSender, LibraryProperties libraryProperties) {
        this.mailSender = mailSender;
        this.libraryProperties = libraryProperties;
    }

    /** Non-blocking: login continues immediately; email sends in background. */
    public void notifyLogin(User user, HttpServletRequest request) {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            return;
        }
        if (mailHost == null || mailHost.isBlank() || mailPassword == null || mailPassword.isBlank()) {
            return;
        }

        String username = user.getUsername();
        String role = user.getRole() != null ? user.getRole().name() : "";
        String email = user.getEmail();
        String ip = AuditService.clientIp(request);
        String device = summarizeDevice(request != null ? request.getHeader("User-Agent") : null);
        String from = libraryProperties.getMailFrom();

        CompletableFuture.runAsync(() -> {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(from);
                message.setTo(email);
                message.setSubject("New Login - Smart Library");
                message.setText("""
                        New Login Detected

                        Username: %s
                        Role: %s
                        Device: %s
                        IP Address: %s
                        Time: %s

                        If this was not you, change your password immediately and contact an administrator.
                        """.formatted(
                        username,
                        role,
                        device,
                        ip != null ? ip : "Unknown",
                        java.time.LocalDateTime.now()
                ));
                mailSender.send(message);
            } catch (Exception e) {
                log.warn("Login notification email failed for {}: {}", username, e.getMessage());
            }
        });
    }

    private static String summarizeDevice(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown device";
        }
        String ua = userAgent.toLowerCase();
        String browser = ua.contains("edg/") ? "Edge"
                : ua.contains("chrome") ? "Chrome"
                : ua.contains("firefox") ? "Firefox"
                : ua.contains("safari") ? "Safari"
                : "Browser";
        String os = ua.contains("windows") ? "Windows"
                : ua.contains("mac") ? "macOS"
                : ua.contains("android") ? "Android"
                : ua.contains("iphone") || ua.contains("ipad") ? "iOS"
                : ua.contains("linux") ? "Linux"
                : "Unknown OS";
        return browser + " on " + os;
    }
}
