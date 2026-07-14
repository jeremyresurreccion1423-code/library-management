package com.smartlibrary.service;

import com.smartlibrary.entity.User;
import com.smartlibrary.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AccountLockoutService {

    public static final int MAX_ATTEMPTS = 5;
    public static final int LOCK_MINUTES = 15;

    private final UserRepository userRepository;
    private final AuditService auditService;

    public AccountLockoutService(UserRepository userRepository, AuditService auditService) {
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    public boolean isLocked(User user) {
        if (user == null || user.getLockedUntil() == null) {
            return false;
        }
        return user.getLockedUntil().isAfter(LocalDateTime.now());
    }

    @Transactional
    public void clearExpiredLock(User user) {
        if (user != null && user.getLockedUntil() != null && !user.getLockedUntil().isAfter(LocalDateTime.now())) {
            user.setLockedUntil(null);
            user.setFailedLoginAttempts(0);
            userRepository.save(user);
        }
    }

    @Transactional
    public void onFailedLogin(String username) {
        if (username == null || username.isBlank()) {
            auditService.logAnonymous("LOGIN_FAILED", "Failed login with empty username");
            return;
        }
        userRepository.findByUsername(username.trim()).ifPresentOrElse(user -> {
            clearExpiredLock(user);
            if (isLocked(user)) {
                auditService.log(user, "LOGIN_FAILED", "User", user.getId(),
                        "Failed login while account locked");
                return;
            }
            int attempts = (user.getFailedLoginAttempts() == null ? 0 : user.getFailedLoginAttempts()) + 1;
            user.setFailedLoginAttempts(attempts);
            if (attempts >= MAX_ATTEMPTS) {
                user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_MINUTES));
                userRepository.save(user);
                auditService.log(user, "ACCOUNT_LOCKED", "User", user.getId(),
                        "Account locked after " + attempts + " failed attempts for " + LOCK_MINUTES + " minutes");
            } else {
                userRepository.save(user);
                auditService.log(user, "LOGIN_FAILED", "User", user.getId(),
                        "Failed login attempt " + attempts + "/" + MAX_ATTEMPTS);
            }
        }, () -> auditService.logAnonymous("LOGIN_FAILED", "Failed login for unknown user: " + username.trim()));
    }

    @Transactional
    public void onSuccessfulLogin(User user) {
        if (user == null) {
            return;
        }
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
    }

    @Transactional
    public void unlock(User user, User actor) {
        if (user == null) {
            return;
        }
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
        auditService.log(actor, "ACCOUNT_UNLOCKED", "User", user.getId(),
                "Account unlocked by admin: " + user.getUsername());
    }

    @Transactional(readOnly = true)
    public List<User> findCurrentlyLocked() {
        return userRepository.findByLockedUntilAfter(LocalDateTime.now());
    }
}
