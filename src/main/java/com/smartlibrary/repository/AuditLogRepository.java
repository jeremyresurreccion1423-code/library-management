package com.smartlibrary.repository;

import com.smartlibrary.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findTop50ByOrderByCreatedAtDesc();

    List<AuditLog> findTop100ByOrderByCreatedAtDesc();

    long countByActionAndCreatedAtAfter(String action, LocalDateTime after);

    List<AuditLog> findByActionAndCreatedAtAfterOrderByCreatedAtDesc(String action, LocalDateTime after);
}
