package com.myhosh.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    boolean existsByRestaurantTableIdAndBookingDateAndBookingTime(Long tableId, LocalDate date, java.time.LocalTime time);

    // Get only bookings for a specific day (e.g., Today)
    List<Booking> findByBookingDateOrderByBookingTimeAsc(LocalDate date);

    // Get all old bookings (History)
    List<Booking> findByBookingDateBeforeOrderByBookingDateDescBookingTimeAsc(LocalDate date);
}