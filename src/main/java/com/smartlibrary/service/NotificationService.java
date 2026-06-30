package com.smartlibrary.service;

import com.smartlibrary.entity.BookIssue;

public interface  NotificationService {

    boolean sendDueReminder(BookIssue issue);

    void sendPasswordReset(String email, String resetLink);

    boolean sendOtpCode(String email, String otpCode);
}
