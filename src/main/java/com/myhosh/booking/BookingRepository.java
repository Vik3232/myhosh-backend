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

    // Fetch single booking with customer + table in 1 SQL LEFT JOIN (prevents LazyInitializationException)
    @Override
    @EntityGraph(attributePaths = {"customer", "restaurantTable"})
    Optional<Booking> findById(Long id);

    // Staff: Today's bookings in 1 SQL query
    @EntityGraph(attributePaths = {"customer", "restaurantTable"})
    List<Booking> findByBookingDateOrderByBookingTimeAsc(LocalDate date);

    // Staff: All active & upcoming bookings in 1 SQL query
    @EntityGraph(attributePaths = {"customer", "restaurantTable"})
    List<Booking> findByBookingDateGreaterThanEqualOrderByBookingDateAscBookingTimeAsc(LocalDate date);

    // Public availability check: 1 SQL query, joins ONLY table (customer PII never even fetched from Postgres)
    @EntityGraph(attributePaths = {"restaurantTable"})
    @Query("SELECT b FROM Booking b WHERE b.bookingDate >= :today AND (b.status IS NULL OR UPPER(b.status) <> 'NO_SHOW') ORDER BY b.bookingDate ASC, b.bookingTime ASC")
    List<Booking> findPublicUpcomingBookings(@Param("today") LocalDate today);

    // Conflict & anti-spam check for a single date in 1 SQL query (excludes NO_SHOW)
    @EntityGraph(attributePaths = {"customer", "restaurantTable"})
    @Query("SELECT b FROM Booking b WHERE b.bookingDate = :date AND (b.status IS NULL OR UPPER(b.status) <> 'NO_SHOW') ORDER BY b.bookingTime ASC")
    List<Booking> findActiveBookingsByDate(@Param("date") LocalDate date);

    // Staff: Past booking history in 1 SQL query
    @EntityGraph(attributePaths = {"customer", "restaurantTable"})
    List<Booking> findByBookingDateBeforeOrderByBookingDateDescBookingTimeAsc(LocalDate date);

    // UK GDPR 6-month purge: 1 single SQL DELETE directly in Postgres (no rows loaded into Java RAM)
    @Modifying
    @Transactional
    @Query("DELETE FROM Booking b WHERE b.bookingDate < :cutoffDate")
    int deleteExpiredBookingsBefore(@Param("cutoffDate") LocalDate cutoffDate);
}