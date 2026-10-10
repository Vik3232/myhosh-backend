package com.myhosh.booking;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

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
    private final JavaMailSender mailSender;

    public BookingController(
            BookingRepository bookingRepository,
            CustomerRepository customerRepository,
            RestaurantTableRepository tableRepository,
            JavaMailSender mailSender
    ) {
        this.bookingRepository = bookingRepository;
        this.customerRepository = customerRepository;
        this.tableRepository = tableRepository;
        this.mailSender = mailSender;
    }

    // --- 1. GET ALL BOOKINGS ---
    @GetMapping
    public java.util.List<Booking> getAllBookings() {
        return bookingRepository.findAll();
    }

    // --- 2. CREATE BOOKING (SENDS CONFIRMATION EMAIL) ---
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

        // SEND CONFIRMATION EMAIL
        if (savedBooking.getCustomer() != null && savedBooking.getCustomer().getEmail() != null) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom("vis628385@gmail.com");
                message.setTo(savedBooking.getCustomer().getEmail());
                message.setSubject("Reservation Confirmed - MyHosh");
                message.setText("Dear " + savedBooking.getCustomer().getFullName() + ",\n\n" +
                        "Your reservation at MyHosh is confirmed!\n\n" +
                        "📅 Date: " + savedBooking.getBookingDate() + "\n" +
                        "⏰ Time: " + savedBooking.getBookingTime() + "\n" +
                        "👥 Party Size: " + savedBooking.getPartySize() + " Guests\n\n" +
                        "We look forward to hosting you.\n\n" +
                        "Best regards,\nMyHosh Management Team");

                mailSender.send(message);
                System.out.println("Confirmation email sent successfully.");
            } catch (Exception e) {
                System.err.println("Failed to send confirmation email: " + e.getMessage());
            }
        }

        return ResponseEntity.ok(savedBooking);
    }

    // --- 3. DIRECT CANCEL BOOKING ENDPOINT ---
    @DeleteMapping("/{id}")
    public ResponseEntity<?> cancelBooking(@PathVariable Long id) {
        if (!bookingRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        bookingRepository.deleteById(id);
        return ResponseEntity.ok("Booking cancelled successfully.");
    }

    // --- 4. UPDATE STAFF INTERNAL NOTES ---
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

    // --- 5. CANCEL BOOKING WITH REASON (SENDS CANCELLATION EMAIL) ---
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

        // Extract customer info before deleting
        String customerEmail = booking.getCustomer() != null ? booking.getCustomer().getEmail() : null;
        String customerName = booking.getCustomer() != null ? booking.getCustomer().getFullName() : "Guest";
        String bookingDate = booking.getBookingDate() != null ? booking.getBookingDate().toString() : "";
        String bookingTime = booking.getBookingTime() != null ? booking.getBookingTime().toString() : "";

        // Delete booking immediately
        bookingRepository.deleteById(id);

        // SEND CANCELLATION EMAIL
        if (customerEmail != null && !customerEmail.isEmpty()) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom("vis628385@gmail.com");
                message.setTo(customerEmail);
                message.setSubject("Reservation Cancellation - MyHosh");
                message.setText("Dear " + customerName + ",\n\n" +
                        "This email is to inform you that your reservation for " + bookingDate +
                        " at " + bookingTime + " has been cancelled.\n\n" +
                        "Reason: " + reason + "\n\n" +
                        "If you have any questions or wish to rebook, please contact us.\n\n" +
                        "Best regards,\nMyHosh Management Team");

                mailSender.send(message);
                System.out.println("Cancellation email sent successfully to " + customerEmail);
            } catch (Exception e) {
                System.err.println("Failed to send cancellation email: " + e.getMessage());
            }
        }

        return ResponseEntity.ok("Booking cancelled. Notification sent to customer.");
    }

    // --- 6. REASSIGN TABLE ENDPOINT ---
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

    // --- 7. UPDATE BOOKING TIME (STAFF & ADMIN) ---
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