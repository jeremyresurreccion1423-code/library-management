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

    public void notifyLogin(User user, HttpServletRequest request) {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            return;
        }
        if (mailHost == null || mailHost.isBlank() || mailPassword == null || mailPassword.isBlank()) {
            return;
        }
        try {
            String ip = AuditService.clientIp(request);
            String ua = request != null ? request.getHeader("User-Agent") : "Unknown";
            String device = summarizeDevice(ua);
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(libraryProperties.getMailFrom());
            message.setTo(user.getEmail());
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
                    user.getUsername(),
                    user.getRole(),
                    device,
                    ip != null ? ip : "Unknown",
                    java.time.LocalDateTime.now()
            ));
            mailSender.send(message);
        } catch (Exception e) {
            log.warn("Login notification email failed for {}: {}", user.getUsername(), e.getMessage());
        }
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
