package com.myhosh.booking;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    boolean existsByRestaurantTableIdAndBookingDateAndBookingTime(Long tableId, LocalDate date, LocalTime time);

    boolean existsByCustomerId(Long customerId);

    @Override
    @EntityGraph(attributePaths = {"customer", "restaurantTable"})
    Optional<Booking> findById(Long id);

    @EntityGraph(attributePaths = {"customer", "restaurantTable"})
    Optional<Booking> findByIdempotencyKey(String idempotencyKey);

    @EntityGraph(attributePaths = {"customer", "restaurantTable"})
    List<Booking> findByBookingDateOrderByBookingTimeAsc(LocalDate date);

    @EntityGraph(attributePaths = {"customer", "restaurantTable"})
    List<Booking> findByBookingDateGreaterThanEqualOrderByBookingDateAscBookingTimeAsc(LocalDate date);

    @EntityGraph(attributePaths = {"restaurantTable"})
    @Query("SELECT b FROM Booking b WHERE b.bookingDate >= :today AND (b.status IS NULL OR UPPER(b.status) NOT IN ('NO_SHOW', 'COMPLETED', 'CANCELLED')) ORDER BY b.bookingDate ASC, b.bookingTime ASC")
    List<Booking> findPublicUpcomingBookings(@Param("today") LocalDate today);

    @EntityGraph(attributePaths = {"customer", "restaurantTable"})
    @Query("SELECT b FROM Booking b WHERE b.bookingDate = :date AND (b.status IS NULL OR UPPER(b.status) NOT IN ('NO_SHOW', 'COMPLETED', 'CANCELLED')) ORDER BY b.bookingTime ASC")
    List<Booking> findActiveBookingsByDate(@Param("date") LocalDate date);

    @EntityGraph(attributePaths = {"customer", "restaurantTable"})
    List<Booking> findByBookingDateBeforeOrderByBookingDateDescBookingTimeAsc(LocalDate date);

    @Modifying
    @Transactional
    @Query("DELETE FROM Booking b WHERE b.bookingDate < :cutoffDate")
    int deleteExpiredBookingsBefore(@Param("cutoffDate") LocalDate cutoffDate);
    @org.springframework.data.jpa.repository.Query("""
            SELECT b FROM Booking b
            JOIN FETCH b.customer
            WHERE UPPER(b.status) = 'COMPLETED'
              AND b.reviewConsent = true
              AND b.completedAt IS NOT NULL
              AND b.completedAt <= :cutoff
              AND b.completedAt >= :oldestAllowed
              AND (b.reviewEmailSent IS NULL OR b.reviewEmailSent = false)
            """)
    java.util.List<Booking> findDueReviewEmails(
            @org.springframework.data.repository.query.Param("cutoff") java.time.Instant cutoff,
            @org.springframework.data.repository.query.Param("oldestAllowed") java.time.Instant oldestAllowed
    );
}