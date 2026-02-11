package com.blackbox.gateway.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByEventTypeAndTimestampAfter(String eventType, Instant after);

    List<AuditLog> findByClientIdAndTimestampAfter(String clientId, Instant after);

    List<AuditLog> findTop100ByOrderByTimestampDesc();
}
