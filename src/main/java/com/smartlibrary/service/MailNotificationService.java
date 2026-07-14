package com.smartlibrary.service;

import com.smartlibrary.config.CentralMailProperties;
import com.smartlibrary.config.LibraryProperties;
import com.smartlibrary.entity.BookIssue;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class MailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(MailNotificationService.class);
    private static final DateTimeFormatter DUE_DATE_FORMAT =
            DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' h:mm a", Locale.ENGLISH);

    private final JavaMailSender mailSender;
    private final LibraryProperties libraryProperties;
    private final CentralMailProperties centralMailProperties;
    private final FineCalculator fineCalculator;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    public MailNotificationService(
            JavaMailSender mailSender,
            LibraryProperties libraryProperties,
            CentralMailProperties centralMailProperties,
            FineCalculator fineCalculator) {
        this.mailSender = mailSender;
        this.libraryProperties = libraryProperties;
        this.centralMailProperties = centralMailProperties;
        this.fineCalculator = fineCalculator;
    }

    public boolean sendDueReminder(BookIssue issue) {
        if (!isMailConfigured()) {
            log.info("[EMAIL REMINDER] To: {} | Book: '{}' | Due: {}",
                    issue.getStudent().getUser().getEmail(),
                    issue.getBook().getTitle(),
                    issue.getDueAt());
            return true;
        }

        try {
            var student = issue.getStudent();
            var user = student.getUser();
            String studentName = student.getFullName() != null ? student.getFullName() : user.getUsername();
            String bookTitle = issue.getBook().getTitle();
            String dueDate = issue.getDueAt().format(DUE_DATE_FORMAT);
            String studentId = student.getStudentId() != null ? student.getStudentId() : "—";
            String finePerDay = fineCalculator.resolveFinePerDay(issue.getBook()).toPlainString();

            String subject = "EduLibrary Reminder: Please return \"" + bookTitle + "\"";
            String plainText = buildDueReminderPlainText(studentName, bookTitle, dueDate, studentId, finePerDay);
            String html = buildDueReminderHtml(studentName, bookTitle, dueDate, studentId, finePerDay);

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(centralMailProperties.getFromEmail(), centralMailProperties.getFromName());
            helper.setTo(user.getEmail());
            helper.setSubject(subject);
            helper.setText(plainText, html);
            mailSender.send(mimeMessage);
            return true;
        } catch (Exception e) {
            log.warn("Could not send email to {}: {}", issue.getStudent().getUser().getEmail(), e.getMessage());
            return false;
        }
    }

    private String buildDueReminderPlainText(
            String studentName, String bookTitle, String dueDate, String studentId, String finePerDay) {
        return "Hello " + studentName + ",\n\n"
                + "This is a friendly reminder from EduLibrary that you have a borrowed book that is due soon.\n\n"
                + "Book: " + bookTitle + "\n"
                + "Due date: " + dueDate + "\n"
                + "Student ID: " + studentId + "\n\n"
                + "Please return the book on or before the due date to avoid overdue fines "
                + "(PHP " + finePerDay + " per day).\n\n"
                + "If you have already returned it, please disregard this message.\n\n"
                + "Thank you,\n"
                + "EduLibrary Team\n"
                + "Laguna University Learning Management System";
    }

    private String buildDueReminderHtml(
            String studentName, String bookTitle, String dueDate, String studentId, String finePerDay) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8"/>
                  <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
                  <title>Book Return Reminder</title>
                </head>
                <body style="margin:0;padding:0;background:#f4f7f5;font-family:Arial,Helvetica,sans-serif;color:#1f2937;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#f4f7f5;padding:24px 12px;">
                    <tr>
                      <td align="center">
                        <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:560px;background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 8px 24px rgba(0,0,0,0.08);">
                          <tr>
                            <td style="background:linear-gradient(135deg,#2e7d32,#1976d2);padding:28px 32px;text-align:center;">
                              <div style="font-size:24px;font-weight:700;color:#ffffff;letter-spacing:0.5px;">EduLibrary</div>
                              <div style="font-size:13px;color:#e8f5e9;margin-top:6px;">Book Return Reminder</div>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:32px;">
                              <p style="margin:0 0 16px;font-size:16px;line-height:1.6;">Hello <strong>%s</strong>,</p>
                              <p style="margin:0 0 24px;font-size:15px;line-height:1.7;color:#4b5563;">
                                This is a friendly reminder that you have a borrowed book that is due soon.
                                Please return it on or before the due date.
                              </p>
                              <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#f8faf9;border:1px solid #e5efe8;border-radius:12px;margin-bottom:24px;">
                                <tr>
                                  <td style="padding:20px 22px;">
                                    <div style="font-size:12px;text-transform:uppercase;letter-spacing:0.08em;color:#6b7280;margin-bottom:8px;">Book Title</div>
                                    <div style="font-size:18px;font-weight:700;color:#166534;margin-bottom:16px;">%s</div>
                                    <div style="font-size:12px;text-transform:uppercase;letter-spacing:0.08em;color:#6b7280;margin-bottom:6px;">Due Date</div>
                                    <div style="font-size:15px;font-weight:600;color:#111827;margin-bottom:16px;">%s</div>
                                    <div style="font-size:12px;text-transform:uppercase;letter-spacing:0.08em;color:#6b7280;margin-bottom:6px;">Student ID</div>
                                    <div style="font-size:15px;font-weight:600;color:#111827;">%s</div>
                                  </td>
                                </tr>
                              </table>
                              <p style="margin:0 0 12px;font-size:14px;line-height:1.6;color:#4b5563;">
                                Overdue fines apply at <strong>PHP %s per day</strong> after the due date.
                              </p>
                              <p style="margin:0;font-size:14px;line-height:1.6;color:#6b7280;">
                                If you have already returned this book, you may ignore this email.
                              </p>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:0 32px 28px;">
                              <div style="border-top:1px solid #e5e7eb;padding-top:18px;font-size:12px;line-height:1.6;color:#9ca3af;text-align:center;">
                                EduLibrary &bull; Laguna University Learning Management System<br/>
                                Please do not reply to this automated message.
                              </div>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(escapeHtml(studentName), escapeHtml(bookTitle), escapeHtml(dueDate),
                escapeHtml(studentId), escapeHtml(finePerDay));
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    public boolean sendOtpCode(String email, String otpCode) {
        if (!isMailConfigured()) {
            log.info("Mail not configured — OTP for {}: {}", email, otpCode);
            return true;
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");
            helper.setFrom(centralMailProperties.getFromEmail(), centralMailProperties.getFromName());
            helper.setTo(email);
            helper.setSubject("Smart Library - Registration OTP Code");
            helper.setText("Welcome to Smart Library!\n\n"
                    + "Your One-Time Password (OTP) for registration is:\n\n"
                    + otpCode + "\n\n"
                    + "This code is valid for " + libraryProperties.getOtpExpiryMinutes() + " minutes. Do not share this code with anyone.\n\n"
                    + "If you did not request this code, please ignore this email.\n\n"
                    + "— Smart Library Team", false);
            mailSender.send(mimeMessage);
            return true;
        } catch (Exception e) {
            log.error("Could not send OTP email to {}: {}", email, e.getMessage(), e);
            return false;
        }
    }

    private boolean isMailConfigured() {
        String password = resolvePassword();
        return StringUtils.hasText(password)
                && StringUtils.hasText(centralMailProperties.getFromEmail());
    }

    private String resolvePassword() {
        String fromEnv = firstNonBlank(
                System.getenv("MAIL_PASSWORD"),
                System.getenv("BREVO_SMTP_PASSWORD"),
                System.getenv("SPRING_MAIL_PASSWORD"));
        if (StringUtils.hasText(fromEnv)) {
            return fromEnv.replace(" ", "").trim();
        }
        return mailPassword == null ? "" : mailPassword.replace(" ", "").trim();
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
