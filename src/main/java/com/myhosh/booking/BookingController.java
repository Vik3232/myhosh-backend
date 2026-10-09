package com.myhosh.booking;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    // --- 1. DIRECT CANCEL BOOKING ENDPOINT ---
    @DeleteMapping("/{id}")
    public ResponseEntity<?> cancelBooking(@PathVariable Long id) {
        if (!bookingRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        bookingRepository.deleteById(id);
        return ResponseEntity.ok("Booking cancelled successfully.");
    }

    // --- 2. UPDATE STAFF INTERNAL NOTES ---
    @PutMapping("/{id}/notes")
    public ResponseEntity<?> updateNotes(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Optional<Booking> bookingOpt = bookingRepository.findById(id);
        if (!bookingOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        Booking booking = bookingOpt.get();
        booking.setStaffNotes(body.get("notes"));
        Booking updated = bookingRepository.save(booking);
        return ResponseEntity.ok(updated);
    }

    // --- 3. CANCEL BOOKING WITH REASON (NOTIFY CUSTOMER) ---
    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancelBookingWithReason(
            @PathVariable Long id,
            @RequestBody Map<String, String> body
    ) {
        Optional<Booking> bookingOpt = bookingRepository.findById(id);
        if (!bookingOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }

        Booking booking = bookingOpt.get();
        String reason = body.getOrDefault("reason", "Cancelled by management");

        // Log notification trigger for customer
        String customerName = (booking.getCustomer() != null) ? booking.getCustomer().getFullName() : "Guest";
        System.out.println("ALERT: Booking cancelled for guest: " + customerName + " | Reason: " + reason);

        bookingRepository.deleteById(id);
        return ResponseEntity.ok("Booking cancelled. Notification logged.");
    }

    // --- 4. REASSIGN TABLE ENDPOINT ---
    @PutMapping("/{id}/table/{tableId}")
    public ResponseEntity<?> reassignTable(@PathVariable Long id, @PathVariable Long tableId) {
        Optional<Booking> bookingOpt = bookingRepository.findById(id);
        if (!bookingOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }

        Optional<RestaurantTable> tableOpt = tableRepository.findById(tableId);
        if (!tableOpt.isPresent()) {
            return ResponseEntity.badRequest().body("Selected table does not exist.");
        }

        Booking booking = bookingOpt.get();
        RestaurantTable newTable = tableOpt.get();

        // Prevent moving to an already occupied table at the exact same date & time
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
    // --- 5. UPDATE BOOKING TIME (STAFF & ADMIN) ---
    @PutMapping("/{id}/time")
    public ResponseEntity<?> updateBookingTime(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Optional<Booking> bookingOpt = bookingRepository.findById(id);
        if (!bookingOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        Booking booking = bookingOpt.get();
        String newTime = body.get("bookingTime");
        if (newTime == null || newTime.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Time cannot be empty.");
        }
        booking.setBookingTime(java.time.LocalTime.parse(newTime));
        Booking updated = bookingRepository.save(booking);
        return ResponseEntity.ok(updated);
    }
}