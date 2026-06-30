package com.smartlibrary.entity;

import com.smartlibrary.model.IssueStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "book_issues",
        indexes = {
                @Index(name = "idx_book_issues_student_status", columnList = "student_profile_id,status"),
                @Index(name = "idx_book_issues_book_status", columnList = "book_id,status"),
                @Index(name = "idx_book_issues_due_status", columnList = "dueAt,status")
        })
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"book", "student"})
public class BookIssue extends BaseEntity {

    @NotNull(message = "Book cannot be null")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id")
    private Book book;

    @NotNull(message = "Student profile cannot be null")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_profile_id")
    private StudentProfile student;

    @NotNull(message = "Issue date cannot be null")
    @Column(nullable = false)
    private LocalDateTime issuedAt;

    @NotNull(message = "Due date cannot be null")
    @Column(nullable = false)
    private LocalDateTime dueAt;

    private LocalDateTime returnedAt;

    @Column(precision = 10, scale = 2)
    private BigDecimal fineAmount = BigDecimal.ZERO;

    @NotNull(message = "Status cannot be null")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private IssueStatus status = IssueStatus.BORROWED;

}
