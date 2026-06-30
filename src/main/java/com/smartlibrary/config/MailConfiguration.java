package com.smartlibrary.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
public class MailConfiguration {

    @Value("${spring.mail.username:}")
    private String configuredUsername;

    @Value("${spring.mail.password:}")
    private String configuredPassword;

    @Bean
    public JavaMailSender javaMailSender() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost("smtp.gmail.com");
        mailSender.setPort(587);

        String username = configuredUsername;
        String password = configuredPassword;

        if (username == null || username.isBlank()) {
            username = System.getenv("MAIL_USERNAME");
        }
        if (password == null || password.isBlank()) {
            password = System.getenv("MAIL_PASSWORD");
        }

        if (username == null) {
            username = "";
        }
        if (password == null) {
            password = "";
        }
        password = password.replace(" ", "");

        mailSender.setUsername(username);
        mailSender.setPassword(password);

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "5000");
        props.put("mail.smtp.writetimeout", "5000");
        
        return mailSender;
    }
}
