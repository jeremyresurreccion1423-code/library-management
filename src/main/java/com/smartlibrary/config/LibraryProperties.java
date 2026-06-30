package com.smartlibrary.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "library")
public class LibraryProperties {

    private int loanDays = 14;
    private BigDecimal finePerDay = new BigDecimal("5.00");
    private String uploadDir = System.getProperty("user.home") + "/smart-library-uploads/ebooks";
    private String mailFrom = "noreply@library.local";
    private int reminderDaysBeforeDue = 2;
    private int otpExpiryMinutes = 5;
    private boolean seedEnabled = true;

    public int getLoanDays() {
        return loanDays;
    }

    public void setLoanDays(int loanDays) {
        this.loanDays = loanDays;
    }

    public BigDecimal getFinePerDay() {
        return finePerDay;
    }

    public void setFinePerDay(BigDecimal finePerDay) {
        this.finePerDay = finePerDay;
    }

    public String getUploadDir() {
        return uploadDir;
    }

    public void setUploadDir(String uploadDir) {
        this.uploadDir = uploadDir;
    }

    public String getMailFrom() {
        return mailFrom;
    }

    public void setMailFrom(String mailFrom) {
        this.mailFrom = mailFrom;
    }

    public int getReminderDaysBeforeDue() {
        return reminderDaysBeforeDue;
    }

    public int getOtpExpiryMinutes() {
        return otpExpiryMinutes;
    }

    public void setOtpExpiryMinutes(int otpExpiryMinutes) {
        this.otpExpiryMinutes = otpExpiryMinutes;
    }

    public void setReminderDaysBeforeDue(int reminderDaysBeforeDue) {
        this.reminderDaysBeforeDue = reminderDaysBeforeDue;
    }

    public boolean isSeedEnabled() {
        return seedEnabled;
    }

    public void setSeedEnabled(boolean seedEnabled) {
        this.seedEnabled = seedEnabled;
    }
}
