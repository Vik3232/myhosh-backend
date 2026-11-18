package com.myhosh.booking;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
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

    // Mutex lock to prevent TOCTOU race conditions on simultaneous bookings
    private final Object bookingLock = new Object();

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

    // Helper: Check if two 120-minute sittings overlap (S1 < E2 && E1 > S2)
    private boolean windowsOverlap(LocalTime start1, LocalTime start2) {
        if (start1 == null || start2 == null) return false;
        int s1 = start1.getHour() * 60 + start1.getMinute();
        int e1 = s1 + 120;
        int s2 = start2.getHour() * 60 + start2.getMinute();
        int e2 = s2 + 120;
        return s1 < e2 && e1 > s2;
    }

    // Helper: Validate operating hours on the server
    private String validateOperatingHours(LocalDate date, LocalTime time) {
        if (date == null || time == null) return "Booking date and time are required.";
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.TUESDAY) {
            return "We are closed on Tuesdays. Please select another day.";
        }
        LocalTime minTime = (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY)
                ? LocalTime.of(12, 0)
                : LocalTime.of(16, 0);
        LocalTime maxTime = (dow == DayOfWeek.SUNDAY)
                ? LocalTime.of(20, 45)
                : LocalTime.of(21, 0);

        if (time.isBefore(minTime) || time.isAfter(maxTime)) {
            return "Reservations for this day must be between " + minTime + " and " + maxTime + ".";
        }
        return null;
    }

    // GET /api/bookings: Returns full data to authenticated Staff, or PII-masked data to public customers
    @GetMapping
    public List<Booking> getAllBookings(
            @RequestHeader(value = "X-Staff-Token", required = false) String staffToken
    ) {
        List<Booking> all = bookingRepository.findAll();
        if (StaffController.isValidStaffToken(staffToken)) {
            return all;
        }

        // Public customer view: Strip Customer PII and staffNotes to comply with UK GDPR
        List<Booking> sanitized = new ArrayList<>();
        for (Booking b : all) {
            Booking pub = new Booking();
            pub.setId(b.getId());
            pub.setBookingDate(b.getBookingDate());
            pub.setBookingTime(b.getBookingTime());
            pub.setPartySize(b.getPartySize());
            pub.setStatus(b.getStatus());
            pub.setRestaurantTable(b.getRestaurantTable());
            sanitized.add(pub);
        }
        return sanitized;
    }

    @PostMapping
    public ResponseEntity<?> createBooking(
            @RequestHeader(value = "X-Staff-Token", required = false) String staffToken,
            @RequestBody Booking newBooking
    ) {
        boolean isStaffOverride = StaffController.isValidStaffToken(staffToken);

        if (newBooking.getBookingDate() == null || newBooking.getBookingTime() == null
                || newBooking.getRestaurantTable() == null || newBooking.getRestaurantTable().getId() == null) {
            return ResponseEntity.badRequest().body("Missing required booking details.");
        }

        // 1. Validate operating hours
        String hoursError = validateOperatingHours(newBooking.getBookingDate(), newBooking.getBookingTime());
        if (hoursError != null) {
            return ResponseEntity.badRequest().body(hoursError);
        }

        synchronized (bookingLock) {
            Optional<RestaurantTable> tableOpt = tableRepository.findById(newBooking.getRestaurantTable().getId());
            if (!tableOpt.isPresent()) {
                return ResponseEntity.badRequest().body("Selected table does not exist.");
            }
            RestaurantTable table = tableOpt.get();

            // 2. Enforce table capacity & yield rules for public online bookings
            if (!isStaffOverride) {
                int guests = newBooking.getPartySize();
                int tNum = table.getTableNumber();
                if (tNum == 4 || guests > table.getCapacity()) {
                    return ResponseEntity.badRequest().body("Selected table cannot accommodate this party size.");
                }
                if (guests < 4 && (tNum == 1 || tNum == 5)) {
                    return ResponseEntity.badRequest().body("Window Tables 1 and 5 require 4 or more guests.");
                }
                if (guests == 1 && !(tNum == 8 || tNum == 9 || tNum == 10)) {
                    return ResponseEntity.badRequest().body("Single-guest bookings are restricted to Tables 8, 9, and 10.");
                }
            }

            // 3. Server-side 120-minute overlap & anti-spam phone check
            List<Booking> sameDayBookings = bookingRepository.findByBookingDateOrderByBookingTimeAsc(newBooking.getBookingDate());
            String cleanInputPhone = (newBooking.getCustomer() != null && newBooking.getCustomer().getPhone() != null)
                    ? newBooking.getCustomer().getPhone().replaceAll("\\D", "")
                    : "";

            for (Booking existing : sameDayBookings) {
                String status = existing.getStatus() != null ? existing.getStatus().toUpperCase() : "CONFIRMED";
                if ("CANCELLED".equals(status) || "NO_SHOW".equals(status)) {
                    continue;
                }

                // Anti-spam: 1 booking per phone number per day for public customers
                if (!isStaffOverride && !cleanInputPhone.isEmpty() && existing.getCustomer() != null && existing.getCustomer().getPhone() != null) {
                    String existingPhone = existing.getCustomer().getPhone().replaceAll("\\D", "");
                    if (!existingPhone.isEmpty() && existingPhone.equals(cleanInputPhone)) {
                        return ResponseEntity.badRequest().body("❌ Anti-Spam Protection: A reservation is already secured under this phone number for this date.");
                    }
                }

                // 120-minute sitting overlap check on the same table
                if (existing.getRestaurantTable() != null
                        && existing.getRestaurantTable().getId().equals(table.getId())
                        && windowsOverlap(newBooking.getBookingTime(), existing.getBookingTime())) {
                    return ResponseEntity.badRequest().body("This table is already reserved within this 2-hour sitting window.");
                }
            }

            if (newBooking.getCustomer() != null && newBooking.getCustomer().getId() == null) {
                customerRepository.save(newBooking.getCustomer());
            }

            if (newBooking.getStatus() == null || newBooking.getStatus().trim().isEmpty()) {
                newBooking.setStatus("CONFIRMED");
            }

            Booking savedBooking = bookingRepository.save(newBooking);

            // Send Email Confirmation (skip internal walk-in/staff placeholder emails)
            if (savedBooking.getCustomer() != null && savedBooking.getCustomer().getEmail() != null) {
                String guestEmail = savedBooking.getCustomer().getEmail().trim();
                if (!guestEmail.isEmpty() && !guestEmail.endsWith("@myhosh.local")) {
                    emailService.sendConfirmationEmail(
                            guestEmail,
                            savedBooking.getCustomer().getFullName(),
                            savedBooking.getBookingDate().toString(),
                            savedBooking.getBookingTime().toString(),
                            savedBooking.getPartySize()
                    );
                }
            }

            return ResponseEntity.ok(savedBooking);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> cancelBooking(
            @RequestHeader(value = "X-Staff-Token", required = false) String staffToken,
            @PathVariable Long id
    ) {
        if (!StaffController.isValidStaffToken(staffToken)) {
            return ResponseEntity.status(401).body("Unauthorized: Staff authentication required.");
        }
        if (!bookingRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        bookingRepository.deleteById(id);
        return ResponseEntity.ok("Booking cancelled successfully.");
    }

    // 1. DASHBOARD: Get only today's bookings (Staff Only)
    @GetMapping("/today")
    public ResponseEntity<?> getTodaysBookings(
            @RequestHeader(value = "X-Staff-Token", required = false) String staffToken
    ) {
        if (!StaffController.isValidStaffToken(staffToken)) {
            return ResponseEntity.status(401).body("Unauthorized: Staff authentication required.");
        }
        return ResponseEntity.ok(bookingRepository.findByBookingDateOrderByBookingTimeAsc(LocalDate.now()));
    }

    // 2. HISTORY: Get all past bookings (Staff Only)
    @GetMapping("/history")
    public ResponseEntity<?> getHistoricalBookings(
            @RequestHeader(value = "X-Staff-Token", required = false) String staffToken
    ) {
        if (!StaffController.isValidStaffToken(staffToken)) {
            return ResponseEntity.status(401).body("Unauthorized: Staff authentication required.");
        }
        return ResponseEntity.ok(bookingRepository.findByBookingDateBeforeOrderByBookingDateDescBookingTimeAsc(LocalDate.now()));
    }

    // 3. STATUS TRACKER: Mark a customer as "Arrived" or "No Show" (Staff Only)
    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateBookingStatus(
            @RequestHeader(value = "X-Staff-Token", required = false) String staffToken,
            @PathVariable Long id,
            @RequestBody Map<String, String> body
    ) {
        if (!StaffController.isValidStaffToken(staffToken)) {
            return ResponseEntity.status(401).body("Unauthorized: Staff authentication required.");
        }
        Optional<Booking> bookingOpt = bookingRepository.findById(id);
        if (!bookingOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }

        Booking booking = bookingOpt.get();
        String newStatus = body.get("status");

        if (newStatus != null && !newStatus.trim().isEmpty()) {
            booking.setStatus(newStatus.toUpperCase());
        }

        Booking updated = bookingRepository.save(booking);
        return ResponseEntity.ok(updated);
    }

    @PutMapping("/{id}/notes")
    public ResponseEntity<?> updateNotes(
            @RequestHeader(value = "X-Staff-Token", required = false) String staffToken,
            @PathVariable Long id,
            @RequestBody Map<String, String> body
    ) {
        if (!StaffController.isValidStaffToken(staffToken)) {
            return ResponseEntity.status(401).body("Unauthorized: Staff authentication required.");
        }
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
            @RequestHeader(value = "X-Staff-Token", required = false) String staffToken,
            @PathVariable Long id,
            @RequestBody Map<String, String> body
    ) {
        if (!StaffController.isValidStaffToken(staffToken)) {
            return ResponseEntity.status(401).body("Unauthorized: Staff authentication required.");
        }
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

        if (customerEmail != null && !customerEmail.isEmpty() && !customerEmail.endsWith("@myhosh.local")) {
            emailService.sendCancellationEmail(customerEmail, customerName, bookingDate, bookingTime, reason);
        }

        return ResponseEntity.ok("Booking cancelled. Notification sent to customer.");
    }

    @PutMapping("/{id}/table/{tableId}")
    public ResponseEntity<?> reassignTable(
            @RequestHeader(value = "X-Staff-Token", required = false) String staffToken,
            @PathVariable Long id,
            @PathVariable Long tableId
    ) {
        if (!StaffController.isValidStaffToken(staffToken)) {
            return ResponseEntity.status(401).body("Unauthorized: Staff authentication required.");
        }
        synchronized (bookingLock) {
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

            List<Booking> sameDayBookings = bookingRepository.findByBookingDateOrderByBookingTimeAsc(booking.getBookingDate());
            for (Booking existing : sameDayBookings) {
                if (existing.getId().equals(booking.getId())) continue;
                String status = existing.getStatus() != null ? existing.getStatus().toUpperCase() : "CONFIRMED";
                if ("CANCELLED".equals(status) || "NO_SHOW".equals(status)) continue;

                if (existing.getRestaurantTable() != null
                        && existing.getRestaurantTable().getId().equals(newTable.getId())
                        && windowsOverlap(booking.getBookingTime(), existing.getBookingTime())) {
                    return ResponseEntity.badRequest().body("Table " + newTable.getTableNumber() + " is already occupied within this 2-hour window.");
                }
            }

            booking.setRestaurantTable(newTable);
            Booking updated = bookingRepository.save(booking);
            return ResponseEntity.ok(updated);
        }
    }

    @PutMapping("/{id}/time")
    public ResponseEntity<?> updateBookingTime(
            @RequestHeader(value = "X-Staff-Token", required = false) String staffToken,
            @PathVariable Long id,
            @RequestBody Map<String, String> body
    ) {
        if (!StaffController.isValidStaffToken(staffToken)) {
            return ResponseEntity.status(401).body("Unauthorized: Staff authentication required.");
        }
        synchronized (bookingLock) {
            Optional<Booking> bookingOpt = bookingRepository.findById(id);
            if (!bookingOpt.isPresent()) {
                return ResponseEntity.notFound().build();
            }
            Booking booking = bookingOpt.get();
            String newTime = body.get("bookingTime");
            if (newTime == null || newTime.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Time cannot be empty.");
            }
            String cleanTime = newTime.trim();
            if (cleanTime.length() == 5) {
                cleanTime = cleanTime + ":00";
            }
            LocalTime parsedTime = LocalTime.parse(cleanTime);
            booking.setBookingTime(parsedTime);
            Booking updated = bookingRepository.save(booking);
            return ResponseEntity.ok(updated);
        }
    }
}