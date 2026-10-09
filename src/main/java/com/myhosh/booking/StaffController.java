package com.myhosh.booking;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingRepository bookingRepository;
    private final CustomerRepository customerRepository;
    private final RestaurantTableRepository tableRepository;

    public BookingController(
            BookingRepository bookingRepository,
            CustomerRepository customerRepository,
            RestaurantTableRepository tableRepository
    ) {
        this.bookingRepository = bookingRepository;
        this.customerRepository = customerRepository;
        this.tableRepository = tableRepository;
    }

    @GetMapping
    public List<Booking> getAllBookings() {
        return bookingRepository.findAll();
    }

    @PostMapping
    public ResponseEntity<?> createBooking(@RequestBody Booking newBooking) {
        boolean isTaken = bookingRepository.existsByRestaurantTableIdAndBookingDateAndBookingTime(
                newBooking.getRestaurantTable().getId(),
                newBooking.getBookingDate(),
                newBooking.getBookingTime()
        );

        if (isTaken) {
            return ResponseEntity.badRequest().body("This table is already reserved for this time slot.");
        }

        if (newBooking.getCustomer() != null && newBooking.getCustomer().getId() == null) {
            customerRepository.save(newBooking.getCustomer());
        }

        Booking savedBooking = bookingRepository.save(newBooking);
        return ResponseEntity.ok(savedBooking);
    }

    // --- CANCEL BOOKING ENDPOINT (STAFF & ADMIN) ---
    @DeleteMapping("/{id}")
    public ResponseEntity<?> cancelBooking(@PathVariable Long id) {
        if (!bookingRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        bookingRepository.deleteById(id);
        return ResponseEntity.ok("Booking cancelled successfully.");
    }

    // --- REASSIGN TABLE ENDPOINT (STAFF & ADMIN) ---
    @PutMapping("/{id}/table/{tableId}")
    public ResponseEntity<?> reassignTable(@PathVariable Long id, @PathVariable Long tableId) {
        var bookingOpt = bookingRepository.findById(id);
        if (bookingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        var tableOpt = tableRepository.findById(tableId);
        if (tableOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("Selected table does not exist.");
        }

        Booking booking = bookingOpt.get();
        RestaurantTable newTable = tableOpt.get();

        // Prevent moving to an already occupied table at the exact same time
        boolean isOccupied = bookingRepository.existsByRestaurantTableIdAndBookingDateAndBookingTime(
                newTable.getId(),
                booking.getBookingDate(),
                booking.getBookingTime()
        );

        if (isOccupied) {
            return ResponseEntity.badRequest().body("Table " + newTable.getTableNumber() + " is already occupied at this date and time.");
        }

        booking.setRestaurantTable(newTable);
        Booking updated = bookingRepository.save(booking);
        return ResponseEntity.ok(updated);
    }
}
