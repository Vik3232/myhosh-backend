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
    private final EmailService emailService;

    public BookingController(
            BookingRepository bookingRepository,
            CustomerRepository customerRepository,
            RestaurantTableRepository tableRepository,
            EmailService emailService
    ) {
        this.bookingRepository = bookingRepository;
        this.customerRepository = customerRepository;
        this.tableRepository = tableRepository;
        this.emailService = emailService;
    }

    @GetMapping
    public java.util.List<Booking> getAllBookings() {
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

        // Call the new EmailService
        if (savedBooking.getCustomer() != null && savedBooking.getCustomer().getEmail() != null) {
            emailService.sendConfirmationEmail(
                    savedBooking.getCustomer().getEmail(),
                    savedBooking.getCustomer().getFullName(),
                    savedBooking.getBookingDate().toString(),
                    savedBooking.getBookingTime().toString(),
                    savedBooking.getPartySize()
            );
        }

        return ResponseEntity.ok(savedBooking);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> cancelBooking(@PathVariable Long id) {
        if (!bookingRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        bookingRepository.deleteById(id);
        return ResponseEntity.ok("Booking cancelled successfully.");
    }

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

        String customerEmail = booking.getCustomer() != null ? booking.getCustomer().getEmail() : null;
        String customerName = booking.getCustomer() != null ? booking.getCustomer().getFullName() : "Guest";
        String bookingDate = booking.getBookingDate() != null ? booking.getBookingDate().toString() : "";
        String bookingTime = booking.getBookingTime() != null ? booking.getBookingTime().toString() : "";

        bookingRepository.deleteById(id);

        // Call the new EmailService
        if (customerEmail != null && !customerEmail.isEmpty()) {
            emailService.sendCancellationEmail(customerEmail, customerName, bookingDate, bookingTime, reason);
        }

        return ResponseEntity.ok("Booking cancelled. Notification sent to customer.");
    }

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