package com.myhosh.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.time.LocalTime;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    // Spring Boot reads this method name and automatically creates a SQL check!
    boolean existsByRestaurantTableIdAndBookingDateAndBookingTime(Long tableId, LocalDate date, LocalTime time);
}