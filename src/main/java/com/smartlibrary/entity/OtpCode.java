package com.smartlibrary.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "otp_codes",
        indexes = {
                @Index(name = "idx_otp_email_verified", columnList = "email,verified"),
                @Index(name = "idx_otp_expires_at", columnList = "expiresAt")
        })
@Data
@EqualsAndHashCode(callSuper = true)
public class OtpCode extends BaseEntity {

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean verified = false;

    public OtpCode() {
    }

    public OtpCode(String email, String code, LocalDateTime expiresAt) {
        this.email = email;
        this.code = code;
        this.expiresAt = expiresAt;
    }
}
