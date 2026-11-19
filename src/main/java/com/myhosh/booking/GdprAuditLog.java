package com.myhosh.booking;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "gdpr_audit_logs")
public class GdprAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long bookingId;

    @Column(nullable = false, length = 40)
    private String action;

    @Column(nullable = false, length = 100)
    private String performedBy;

    @Column(nullable = false, length = 30)
    private String actorRole;

    @Column(nullable = false)
    private Instant performedAt;

    public Long getId() { return id; }
    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getPerformedBy() { return performedBy; }
    public void setPerformedBy(String performedBy) { this.performedBy = performedBy; }

    public String getActorRole() { return actorRole; }
    public void setActorRole(String actorRole) { this.actorRole = actorRole; }

    public Instant getPerformedAt() { return performedAt; }
    public void setPerformedAt(Instant performedAt) { this.performedAt = performedAt; }
}