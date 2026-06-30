package com.smartlibrary.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Entity
@Table(name = "admin_revenue")
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminRevenue extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_issue_id")
    private BookIssue bookIssue;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private String revenueType;

    public AdminRevenue() {
    }

    public AdminRevenue(BookIssue bookIssue, BigDecimal amount, String revenueType) {
        this.bookIssue = bookIssue;
        this.amount = amount;
        this.revenueType = revenueType;
    }
}
