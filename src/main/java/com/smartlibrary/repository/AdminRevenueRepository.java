package com.smartlibrary.repository;

import com.smartlibrary.entity.AdminRevenue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AdminRevenueRepository extends JpaRepository<AdminRevenue, Long> {

    List<AdminRevenue> findByBookIssueId(Long bookIssueId);

    void deleteByBookIssueId(Long bookIssueId);

    @Query("SELECT SUM(ar.amount) FROM AdminRevenue ar WHERE ar.createdAt BETWEEN :start AND :end")
    BigDecimal getTotalRevenueBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT SUM(ar.amount) FROM AdminRevenue ar")
    BigDecimal getTotalRevenue();

    @Query("SELECT ar FROM AdminRevenue ar ORDER BY ar.createdAt DESC")
    List<AdminRevenue> findAllByCreatedAtDesc();

    @Query("SELECT ar FROM AdminRevenue ar WHERE ar.createdAt BETWEEN :start AND :end ORDER BY ar.createdAt DESC")
    List<AdminRevenue> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
}
