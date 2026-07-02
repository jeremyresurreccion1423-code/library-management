package com.smartlibrary.entity;

import com.smartlibrary.model.UserRole;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Entity
@Table(
        name = "users",
        schema = "public",
        indexes = {
                @Index(name = "idx_users_email", columnList = "email"),
                @Index(name = "idx_users_role_enabled", columnList = "role,enabled")
        })
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = "studentProfile")
public class User extends BaseEntity {

    @NotBlank(message = "Username cannot be blank")
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(length = 50)
    private String fullName;

    @NotBlank(message = "Password cannot be blank")
    @Column(nullable = false)
    private String password;
 
    @Email(message = "Email should be valid")
    @NotBlank(message = "Email cannot be blank")
    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @NotNull(message = "Role cannot be null")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UserRole role;

    @Column(nullable = false)
    private boolean enabled = true;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    private StudentProfile studentProfile;

    public void setStudentProfile(StudentProfile studentProfile) {
        this.studentProfile = studentProfile;
        if (studentProfile != null) {
            studentProfile.setUser(this);
        }
    }
}
