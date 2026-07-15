package com.smartlibrary.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.mail.internet.MimeMessage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Central LU mail transport: Brevo HTTPS API (Railway-safe) with Brevo SMTP fallback.
 * When BREVO_API_KEY is present, SMTP is never used.
 */
@Component
public class BrevoMailTransport {

    private static final Logger log = LoggerFactory.getLogger(BrevoMailTransport.class);
    private static final String BREVO_API_URL = "https://api.brevo.com/v3/smtp/email";

    private final JavaMailSender mailSender;
    private final CentralMailProperties centralMailProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @Value("${central.mail.brevo-api-key:${BREVO_API_KEY:}}")
    private String configuredApiKey;

    @Value("${spring.mail.password:}")
    private String configuredSmtpPassword;

    public BrevoMailTransport(
            JavaMailSender mailSender,
            CentralMailProperties centralMailProperties,
            ObjectMapper objectMapper) {
        this.mailSender = mailSender;
        this.centralMailProperties = centralMailProperties;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void logMailTransportMode() {
        if (StringUtils.hasText(resolveApiKey())) {
            log.info("BREVO_API_KEY detected — emails will use Brevo HTTPS API ({})", BREVO_API_URL);
        } else if (StringUtils.hasText(resolveSmtpPassword())) {
            log.warn("BREVO_API_KEY not set — emails will use Brevo SMTP fallback (blocked on Railway Hobby)");
        } else {
            log.warn("Mail not configured: set BREVO_API_KEY (preferred) or MAIL_PASSWORD for SMTP fallback");
        }
    }

    public boolean isConfigured() {
        return StringUtils.hasText(centralMailProperties.getFromEmail())
                && (StringUtils.hasText(resolveApiKey()) || StringUtils.hasText(resolveSmtpPassword()));
    }

    public void sendText(String to, String subject, String textBody) throws Exception {
        send(to, subject, textBody, null);
    }

    public void sendHtml(String to, String subject, String textBody, String htmlBody) throws Exception {
        send(to, subject, textBody, htmlBody);
    }

    private void send(String to, String subject, String textBody, String htmlBody) throws Exception {
        if (!isConfigured()) {
            throw new IllegalStateException(
                    "Mail is not configured. On Railway set BREVO_API_KEY (HTTPS). "
                            + "SMTP ports are blocked on Hobby/Free plans.");
        }

        String apiKey = resolveApiKey();
        if (StringUtils.hasText(apiKey)) {
            log.info("Sending email via Brevo HTTPS API to {} (SMTP skipped; BREVO_API_KEY present)", to);
            sendViaHttpsApi(apiKey, to, subject, textBody, htmlBody);
            return;
        }

        log.info("Sending email via Brevo SMTP fallback to {} (BREVO_API_KEY missing)", to);
        try {
            sendViaSmtp(to, subject, textBody, htmlBody);
        } catch (Exception smtpEx) {
            if (isConnectivityFailure(smtpEx)) {
                throw new IllegalStateException(
                        "Brevo SMTP timed out (Railway blocks ports 587/465 on Hobby). "
                                + "Create a Brevo API key (SMTP & API → API Keys) and set BREVO_API_KEY on Railway.",
                        smtpEx);
            }
            throw smtpEx;
        }
    }

    private void sendViaHttpsApi(String apiKey, String to, String subject, String textBody, String htmlBody)
            throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sender", Map.of(
                "name", centralMailProperties.getFromName(),
                "email", centralMailProperties.getFromEmail()));
        payload.put("to", List.of(Map.of("email", to)));
        payload.put("subject", subject);
        if (StringUtils.hasText(textBody)) {
            payload.put("textContent", textBody);
        }
        if (StringUtils.hasText(htmlBody)) {
            payload.put("htmlContent", htmlBody);
        }

        String json = objectMapper.writeValueAsString(payload);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BREVO_API_URL))
                .timeout(Duration.ofSeconds(20))
                .header("accept", "application/json")
                .header("content-type", "application/json")
                .header("api-key", apiKey.trim())
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            log.info("Email sent via Brevo HTTPS API to {} from {}", to, centralMailProperties.getFromHeader());
            return;
        }
        throw new IllegalStateException("Brevo HTTPS API failed (" + response.statusCode() + "): "
                + abbreviate(response.body()));
    }

    private void sendViaSmtp(String to, String subject, String textBody, String htmlBody) throws Exception {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        boolean multipart = StringUtils.hasText(htmlBody);
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, multipart, "UTF-8");
        helper.setFrom(centralMailProperties.getFromEmail(), centralMailProperties.getFromName());
        helper.setTo(to);
        helper.setSubject(subject);
        if (multipart) {
            helper.setText(textBody == null ? "" : textBody, htmlBody);
        } else {
            helper.setText(textBody == null ? "" : textBody, false);
        }
        mailSender.send(mimeMessage);
        log.info("Email sent via Brevo SMTP fallback to {} from {}", to, centralMailProperties.getFromHeader());
    }

    private String resolveApiKey() {
        String fromEnv = firstNonBlank(
                System.getenv("BREVO_API_KEY"),
                System.getenv("BREVO_APIKEY"));
        if (StringUtils.hasText(fromEnv)) {
            return fromEnv.trim();
        }
        return configuredApiKey == null ? "" : configuredApiKey.trim();
    }

    private String resolveSmtpPassword() {
        String fromEnv = firstNonBlank(
                System.getenv("MAIL_PASSWORD"),
                System.getenv("BREVO_SMTP_PASSWORD"),
                System.getenv("SPRING_MAIL_PASSWORD"));
        if (StringUtils.hasText(fromEnv)) {
            return fromEnv.replace(" ", "").trim();
        }
        return configuredSmtpPassword == null ? "" : configuredSmtpPassword.replace(" ", "").trim();
    }

    private static boolean isConnectivityFailure(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            String msg = current.getMessage();
            String name = current.getClass().getName();
            if (name.contains("SocketTimeoutException")
                    || name.contains("ConnectException")
                    || name.contains("MailConnectException")
                    || (msg != null && (msg.toLowerCase().contains("timed out")
                    || msg.toLowerCase().contains("couldn't connect")
                    || msg.toLowerCase().contains("network is unreachable")))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static String abbreviate(String body) {
        if (body == null || body.isBlank()) {
            return "no response body";
        }
        String trimmed = body.trim();
        return trimmed.length() > 180 ? trimmed.substring(0, 177) + "..." : trimmed;
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
