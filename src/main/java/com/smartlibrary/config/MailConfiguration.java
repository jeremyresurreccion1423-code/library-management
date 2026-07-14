package com.smartlibrary.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.util.StringUtils;

import java.util.Properties;

/**
 * Brevo SMTP transport for the LU Centralized System (Smart Library).
 * Credentials come from spring.mail.* / MAIL_USERNAME / MAIL_PASSWORD (or BREVO_SMTP_*).
 */
@Configuration
@EnableConfigurationProperties(CentralMailProperties.class)
public class MailConfiguration {

    @Value("${spring.mail.host:smtp-relay.brevo.com}")
    private String host;

    @Value("${spring.mail.port:587}")
    private int port;

    @Value("${spring.mail.username:}")
    private String configuredUsername;

    @Value("${spring.mail.password:}")
    private String configuredPassword;

    @Bean
    public JavaMailSender javaMailSender() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(host);
        mailSender.setPort(port);
        mailSender.setUsername(resolveUsername());
        mailSender.setPassword(resolvePassword());

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.smtp.ssl.trust", host);
        props.put("mail.smtp.connectiontimeout", "20000");
        props.put("mail.smtp.timeout", "20000");
        props.put("mail.smtp.writetimeout", "20000");
        return mailSender;
    }

    private String resolveUsername() {
        String fromEnv = firstNonBlank(
                System.getenv("MAIL_USERNAME"),
                System.getenv("BREVO_SMTP_USERNAME"),
                System.getenv("SPRING_MAIL_USERNAME"));
        if (StringUtils.hasText(fromEnv)) {
            return fromEnv.trim();
        }
        return configuredUsername == null ? "" : configuredUsername.trim();
    }

    private String resolvePassword() {
        String fromEnv = firstNonBlank(
                System.getenv("MAIL_PASSWORD"),
                System.getenv("BREVO_SMTP_PASSWORD"),
                System.getenv("SPRING_MAIL_PASSWORD"));
        String raw = StringUtils.hasText(fromEnv) ? fromEnv : configuredPassword;
        if (raw == null) {
            return "";
        }
        return raw.replace(" ", "").trim();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
