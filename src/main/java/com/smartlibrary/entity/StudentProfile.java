package com.smartlibrary.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Entity
@Table(
        name = "student_profiles",
        indexes = {
                @Index(name = "idx_student_profiles_student_id", columnList = "student_id"),
                @Index(name = "idx_student_profiles_last_name", columnList = "last_name")
        })
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = "user")
public class StudentProfile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @NotBlank(message = "Student ID cannot be blank")
    @Column(name = "student_id", nullable = false, unique = true, length = 32)
    private String studentId;

    @NotBlank(message = "Full name cannot be blank")
    @Column(nullable = false, length = 160)
    private String fullName;

    @Column(name = "first_name", length = 80)
    private String firstName;

    @Column(name = "last_name", length = 80)
    private String lastName;

    @Min(value = 5, message = "Age must be at least 5")
    @Column(name = "age")
    private Integer age;

    @Column(length = 32)
    private String phone;

    @Column(length = 120)
    private String course;
}
