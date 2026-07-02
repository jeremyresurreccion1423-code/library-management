package com.smartlibrary.repository;

import com.smartlibrary.entity.AdminRevenue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminRevenueRepository extends JpaRepository<AdminRevenue, Long> {

    void deleteByBookIssueId(Long bookIssueId);
}
