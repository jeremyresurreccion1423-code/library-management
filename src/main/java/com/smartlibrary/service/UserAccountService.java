package com.smartlibrary.service;

import com.smartlibrary.config.LibraryProperties;
import com.smartlibrary.entity.OtpCode;
import com.smartlibrary.entity.StudentProfile;
import com.smartlibrary.entity.User;
import com.smartlibrary.model.UserRole;
import com.smartlibrary.repository.OtpRepository;
import com.smartlibrary.repository.StudentProfileRepository;
import com.smartlibrary.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
public class UserAccountService {
    private static final Logger log = LoggerFactory.getLogger(UserAccountService.class);
    private static final SecureRandom OTP_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final StudentIdService studentIdService;
    private final MailNotificationService mailNotificationService;
    private final OtpRepository otpRepository;
    private final LibraryProperties libraryProperties;
    private final SharedAttendanceStudentProfileSyncService sharedAttendanceStudentProfileSyncService;

    public UserAccountService(
            UserRepository userRepository,
            StudentProfileRepository studentProfileRepository,
            PasswordEncoder passwordEncoder,
            StudentIdService studentIdService,
            MailNotificationService mailNotificationService,
            OtpRepository otpRepository,
            LibraryProperties libraryProperties,
            SharedAttendanceStudentProfileSyncService sharedAttendanceStudentProfileSyncService) {
        this.userRepository = userRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.studentIdService = studentIdService;
        this.mailNotificationService = mailNotificationService;
        this.otpRepository = otpRepository;
        this.libraryProperties = libraryProperties;
        this.sharedAttendanceStudentProfileSyncService = sharedAttendanceStudentProfileSyncService;
    }

    @Transactional
    public StudentProfile registerStudent(
            String username, String rawPassword, String email, String fullName, String phone, String course) {
        username = (username == null ? "" : username.trim());
        email = normalizeEmail(email);
        fullName = (fullName == null ? "" : fullName.trim());

        if (username.isBlank()) {
            throw new IllegalArgumentException("Username is required");
        }
        if (!username.matches("[A-Za-z0-9._-]{5,20}")) {
            throw new IllegalArgumentException(
                    "Username must be 5-20 characters and may include letters, numbers, dots, underscores, or hyphens");
        }
        validateStrongPassword(rawPassword);
        if (email.isBlank() || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("Please enter a valid email address");
        }
        if (fullName.isBlank() || fullName.length() < 3 || fullName.split("\\s+").length < 2) {
            throw new IllegalArgumentException("Full name is required and should include first and last name");
        }
        validateLettersOnlyField(fullName, "Full name");
        phone = (phone == null ? "" : phone.trim());
        if (!phone.isBlank() && !phone.matches("^09[0-9]{9}$")) {
            throw new IllegalArgumentException("Phone number must start with 09 and be exactly 11 digits");
        }
        course = (course == null ? "" : course.trim());
        if (!course.isBlank() && course.length() < 2) {
            throw new IllegalArgumentException("Course name must be at least 2 characters if provided");
        }
        if (!course.isBlank()) {
            validateLettersOnlyField(course, "Course");
        }
        
        userRepository.findByUsername(username).ifPresent(user -> {
            if (!user.isEnabled()) {
                userRepository.delete(user);
            }
        });
        
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already taken");
        }
        
        deleteUnverifiedAccount(email);
        
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new IllegalArgumentException("An account with that email already exists");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setEmail(email);
        user.setFullName(fullName);
        user.setRole(UserRole.STUDENT);
        user.setEnabled(false);
        userRepository.save(user);

        StudentProfile profile = new StudentProfile();
        profile.setStudentId(studentIdService.generateNextStudentId());
        profile.setFullName(fullName);
        profile.setPhone(phone);
        profile.setCourse(course);
        profile.setUser(user);
        user.setStudentProfile(profile);
        
        studentProfileRepository.save(profile);
        userRepository.save(user);
        try {
            sharedAttendanceStudentProfileSyncService.syncFromLibraryRegistration(user, profile);
        } catch (Exception syncEx) {
            // Library registration must succeed even if Attendance mirror has a temporary conflict.
            log.warn("Attendance sync after Library registration failed for {}: {}",
                    user.getUsername(), syncEx.getMessage());
        }
        return profile;
    }

    @Transactional
    public void changePassword(String username, String oldRaw, String newRaw) {
        User u = userRepository.findByUsername(username).orElseThrow();
        if (!passwordEncoder.matches(oldRaw, u.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        validateStrongPassword(newRaw);
        u.setPassword(passwordEncoder.encode(newRaw));
        userRepository.save(u);
    }

    @Transactional
    public void validateCurrentPassword(String username, String currentPassword) {
        User u = userRepository.findByUsername(username).orElseThrow();
        if (!passwordEncoder.matches(currentPassword, u.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
    }

    @Transactional
    public void changePasswordWithoutOld(String username, String newRaw) {
        User u = userRepository.findByUsername(username).orElseThrow();
        validateStrongPassword(newRaw);
        u.setPassword(passwordEncoder.encode(newRaw));
        userRepository.save(u);
    }

    @Transactional
    public void requestPasswordResetOtp(String emailOrUsername) {
        User u = userRepository.findByUsername(emailOrUsername).orElse(null);
        if (u == null) {
            u = userRepository.findByEmailIgnoreCase(emailOrUsername).orElse(null);
        }
        if (u == null) {
            throw new IllegalArgumentException("No account found with that username or email");
        }
        generateAndSendOtp(u.getEmail());
    }

    @Transactional
    public void resetPasswordWithOtp(String emailOrUsername, String otpCode, String newRaw) {
        User u = userRepository.findByUsername(emailOrUsername).orElse(null);
        if (u == null) {
            u = userRepository.findByEmailIgnoreCase(emailOrUsername).orElse(null);
        }
        if (u == null) {
            throw new IllegalArgumentException("No account found");
        }
        boolean verified = verifyOtp(u.getEmail(), otpCode);
        if (!verified) {
            throw new IllegalArgumentException("Invalid or expired OTP");
        }
        validateStrongPassword(newRaw);
        u.setPassword(passwordEncoder.encode(newRaw));
        userRepository.save(u);
    }

    @Transactional
    public void updateStudentProfile(String username, String fullName, String phone, String course) {
        User u = userRepository.findByUsername(username).orElseThrow();
        StudentProfile profile = studentProfileRepository.findByUserId(u.getId())
                .orElseThrow(() -> new IllegalStateException("Not a student account"));
        fullName = fullName == null ? "" : fullName.trim();
        phone = phone == null ? "" : phone.trim();
        course = course == null ? "" : course.trim();
        validateLettersOnlyField(fullName, "Full name");
        if (!course.isBlank()) {
            validateLettersOnlyField(course, "Course");
        }
        profile.setFullName(fullName);
        profile.setPhone(phone);
        profile.setCourse(course);
        studentProfileRepository.save(profile);
    }

    @Transactional
    public User registerAdmin(String username, String rawPassword) {
        username = (username == null ? "" : username.trim());
        if (username.isBlank()) {
            throw new IllegalArgumentException("Username is required");
        }
        if (!username.matches("[A-Za-z0-9._-]{5,20}")) {
            throw new IllegalArgumentException(
                    "Username must be 5-20 characters and may include letters, numbers, dots, underscores, or hyphens");
        }
        validateStrongPassword(rawPassword);
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already taken");
        }
        User user = new User();
        user.setUsername(username);
        user.setEmail(username.toLowerCase() + "@admin.local");
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(UserRole.ADMIN);
        user.setEnabled(true);
        userRepository.save(user);
        return user;
    }

    public String resolveOtpDeliveryEmail(String email) {
        email = normalizeEmail(email);
        if (email.isBlank()) {
            return email;
        }
        if (!email.contains("@")) {
            return "edulibrary67+" + email + "@gmail.com";
        }
        if (email.endsWith("@library.local") || email.endsWith("@admin.local")) {
            String name = email.substring(0, email.indexOf("@"));
            return "edulibrary67+" + name + "@gmail.com";
        }
        return email;
    }

    @Transactional
    public void generateAndSendOtp(String email) {
        String deliveryEmail = resolveOtpDeliveryEmail(email);

        otpRepository.findByEmailAndVerifiedFalse(deliveryEmail).ifPresent(otpRepository::delete);

        String otpCode = String.format("%06d", OTP_RANDOM.nextInt(1_000_000));

        OtpCode otp = new OtpCode(deliveryEmail, otpCode, LocalDateTime.now().plusMinutes(libraryProperties.getOtpExpiryMinutes()));
        otpRepository.save(otp);

        boolean sent = mailNotificationService.sendOtpCode(deliveryEmail, otpCode);
        if (!sent) {
            throw new IllegalArgumentException(
                    "Could not send OTP email. Check Brevo SMTP MAIL_PASSWORD settings and try again.");
        }
    }

    @Transactional
    public boolean verifyOtp(String email, String otpCode) {
        String deliveryEmail = resolveOtpDeliveryEmail(email);
        OtpCode otp = otpRepository.findByEmailAndCodeAndVerifiedFalse(deliveryEmail, otpCode)
                .orElseThrow(() -> new IllegalArgumentException("Invalid OTP code"));

        if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("OTP has expired");
        }

        otp.setVerified(true);
        otpRepository.save(otp);
        return true;
    }

    @Transactional
    public StudentProfile registerAndSendOtp(
            String username, String rawPassword, String email, String fullName, String phone, String course) {
        StudentProfile profile = registerStudent(username, rawPassword, email, fullName, phone, course);
        try {
            generateAndSendOtp(email);
        } catch (Exception mailEx) {
            // Keep unverified account so the OTP screen can open and user can Resend.
            log.warn("OTP email failed after registration for {}: {}", email, mailEx.getMessage());
        }
        return profile;
    }

    @Transactional
    public boolean verifyRegistrationOtp(String email, String otpCode) {
        email = normalizeEmail(email);
        boolean verified = verifyOtp(email, otpCode);

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setEnabled(true);
        userRepository.save(user);

        studentProfileRepository.findByUserId(user.getId()).ifPresent(profile -> {
            try {
                sharedAttendanceStudentProfileSyncService.syncFromLibraryRegistration(user, profile);
            } catch (Exception syncEx) {
                log.warn("Attendance sync after OTP verify failed for {}: {}",
                        user.getUsername(), syncEx.getMessage());
            }
        });

        return verified;
    }

    @Transactional
    public void deleteUnverifiedAccount(String email) {
        email = normalizeEmail(email);
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user != null && !user.isEnabled()) {
            studentProfileRepository.findByUserId(user.getId()).ifPresent(studentProfileRepository::delete);
            userRepository.delete(user);
        }
    }

    public boolean emailExists(String email) {
        email = normalizeEmail(email);
        return userRepository.findByEmailIgnoreCase(email).isPresent();
    }

    public String getLastGeneratedOtp(String email) {
        String deliveryEmail = resolveOtpDeliveryEmail(email);
        return otpRepository.findByEmailAndVerifiedFalse(deliveryEmail)
                .map(OtpCode::getCode)
                .orElse(null);
    }

    public String findStudentIdByEmail(String email) {
        email = normalizeEmail(email);
        if (email == null || email.isBlank()) {
            return null;
        }
        return userRepository.findByEmailIgnoreCase(email)
                .map(User::getStudentProfile)
                .map(StudentProfile::getStudentId)
                .orElse(null);
    }

    @Transactional
    public void updateStudentDemographicsByEmail(String email, String firstName, String lastName) {
        email = normalizeEmail(email);
        if (email == null || email.isBlank()) {
            return;
        }
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        StudentProfile profile = user.getStudentProfile();
        if (profile == null) {
            return;
        }
        profile.setFirstName(firstName == null ? null : firstName.trim());
        profile.setLastName(lastName == null ? null : lastName.trim());
        studentProfileRepository.save(profile);
    }

    private static void validateLettersOnlyField(String value, String fieldLabel) {
        if (value != null && !value.isBlank() && !value.matches("^[A-Za-z\\s]+$")) {
            throw new IllegalArgumentException(fieldLabel + " must contain letters and spaces only (no numbers).");
        }
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private static void validateStrongPassword(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }
        if (!rawPassword.matches(".*[A-Z].*")
                || !rawPassword.matches(".*[a-z].*")
                || !rawPassword.matches(".*\\d.*")
                || !rawPassword.matches(".*[!@#$%^&*()\\-_=+\\[\\]{};:'\",.<>/?].*")) {
            throw new IllegalArgumentException(
                    "Password must include uppercase and lowercase letters, a number, and a special character");
        }
    }
}
