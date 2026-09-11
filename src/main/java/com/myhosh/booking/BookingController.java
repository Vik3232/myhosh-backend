package com.myhosh.booking;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingRepository bookingRepository;
    private final CustomerRepository customerRepository;

    public BookingController(BookingRepository bookingRepository, CustomerRepository customerRepository) {
        this.bookingRepository = bookingRepository;
        this.customerRepository = customerRepository;
    }

    @GetMapping
    public List<Booking> getAllBookings() {
        return bookingRepository.findAll();
    }

    @PostMapping
    public ResponseEntity<?> createBooking(@RequestBody Booking newBooking) {
        // 1. Check if the table is already taken!
        boolean isTaken = bookingRepository.existsByRestaurantTableIdAndBookingDateAndBookingTime(
                newBooking.getRestaurantTable().getId(),
                newBooking.getBookingDate(),
                newBooking.getBookingTime()
        );

        if (isTaken) {
            // Send back a 400 Bad Request error to the website
            return ResponseEntity.badRequest().body("❌ Sorry, this table is already booked at that time!");
        }

        // 2. If it is free, save the customer and the booking!
        Customer savedCustomer = customerRepository.save(newBooking.getCustomer());
        newBooking.setCustomer(savedCustomer);

        Booking savedBooking = bookingRepository.save(newBooking);
        return ResponseEntity.ok(savedBooking); // Send back a 200 OK success
    }
}