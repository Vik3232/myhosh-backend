package com.myhosh.booking;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GdprAuditLogRepository extends JpaRepository<GdprAuditLog, Long> {
}
