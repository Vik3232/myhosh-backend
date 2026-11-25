package com.myhosh.booking;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "bookings",
        indexes = {
                @Index(name = "idx_bookings_booking_date", columnList = "booking_date"),
                @Index(name = "idx_bookings_date_table", columnList = "booking_date, table_id")
        }
)
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_id")
    private RestaurantTable restaurantTable;

    private LocalDate bookingDate;
    private LocalTime bookingTime;
    private Integer partySize;
    private String specialRequests;

    @Column(length = 1000)
    private String staffNotes;

    private String status = "CONFIRMED";

    @Column(name = "idempotency_key", unique = true, length = 64)
    private String idempotencyKey;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "review_email_sent")
    private Boolean reviewEmailSent = false;

    @Column(name = "review_consent")
    private Boolean reviewConsent = false;

    @Column(name = "review_consent_at")
    private Instant reviewConsentAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public RestaurantTable getRestaurantTable() {
        return restaurantTable;
    }

    public void setRestaurantTable(RestaurantTable restaurantTable) {
        this.restaurantTable = restaurantTable;
    }

    public LocalDate getBookingDate() {
        return bookingDate;
    }

    public void setBookingDate(LocalDate bookingDate) {
        this.bookingDate = bookingDate;
    }

    public LocalTime getBookingTime() {
        return bookingTime;
    }

    public void setBookingTime(LocalTime bookingTime) {
        this.bookingTime = bookingTime;
    }

    public Integer getPartySize() {
        return partySize;
    }

    public void setPartySize(Integer partySize) {
        this.partySize = partySize;
    }

    public String getSpecialRequests() {
        return specialRequests;
    }

    public void setSpecialRequests(String specialRequests) {
        this.specialRequests = specialRequests;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;

    }


    public String getStaffNotes() {
        return staffNotes;
    }

    public void setStaffNotes(String staffNotes) {
        this.staffNotes = staffNotes;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Boolean getReviewEmailSent() {
        return reviewEmailSent;
    }

    public void setReviewEmailSent(Boolean reviewEmailSent) {
        this.reviewEmailSent = reviewEmailSent;
    }

    public Boolean getReviewConsent() {
        return reviewConsent;
    }

    public void setReviewConsent(Boolean reviewConsent) {
        this.reviewConsent = reviewConsent;
    }

    public Instant getReviewConsentAt() {
        return reviewConsentAt;
    }

    public void setReviewConsentAt(Instant reviewConsentAt) {
        this.reviewConsentAt = reviewConsentAt;
    }
}