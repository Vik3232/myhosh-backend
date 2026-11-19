package com.myhosh.booking;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private static final ZoneId LONDON_ZONE = ZoneId.of("Europe/London");

    private final BookingRepository bookingRepository;
    private final CustomerRepository customerRepository;
    private final RestaurantTableRepository tableRepository;
    private final EmailService emailService;
    private final StaffController staffController;

    // Thread-safe concurrency lock to prevent double-booking race conditions
    private final Object bookingLock = new Object();

    public BookingController(BookingRepository bookingRepository,
                             CustomerRepository customerRepository,
                             RestaurantTableRepository tableRepository,
                             EmailService emailService,
                             StaffController staffController) {
        this.bookingRepository = bookingRepository;
        this.customerRepository = customerRepository;
        this.tableRepository = tableRepository;
        this.emailService = emailService;
        this.staffController = staffController;
    }

    // --- PHASE 3: UK GDPR 6-MONTH AUTOMATIC DATA RETENTION PURGE ---
    // Runs 30 seconds after server boot and once every 24 hours thereafter
    @Scheduled(initialDelay = 30000, fixedRate = 86400000)
    public void purgeExpiredGdprBookings() {
        try {
            LocalDate cutoffDate = LocalDate.now(LONDON_ZONE).minusMonths(6);
            int deletedCount = bookingRepository.deleteExpiredBookingsBefore(cutoffDate);

            if (deletedCount > 0) {
                System.out.println("🔒 [UK GDPR Auto-Purge] Deleted " + deletedCount
                        + " reservations older than 6 months (before " + cutoffDate + ").");
            } else {
                System.out.println("🔒 [UK GDPR Auto-Purge] Checked retention policy: 0 bookings older than " + cutoffDate + ".");
            }
        } catch (Exception e) {
            System.err.println("⚠️ [UK GDPR Auto-Purge] Error during scheduled cleanup: " + e.getMessage());
        }
    }

    // --- M3 GDPR PRIVACY HELPER: Scrub Customer PII for Unauthenticated Public Requests ---
    private Booking maskPublicBooking(Booking b) {
        Booking masked = new Booking();
        masked.setId(b.getId());
        masked.setBookingDate(b.getBookingDate());
        masked.setBookingTime(b.getBookingTime());
        masked.setPartySize(b.getPartySize());
        masked.setStatus(b.getStatus());
        masked.setRestaurantTable(b.getRestaurantTable());
        // customer, specialRequests, and staffNotes remain null for public callers
        return masked;
    }

    private boolean isRangesOverlap(LocalTime start1, LocalTime start2) {
        if (start1 == null || start2 == null) return false;
        LocalTime end1 = start1.plusMinutes(120);
        LocalTime end2 = start2.plusMinutes(120);
        return start1.isBefore(end2) && end1.isAfter(start2);
    }

    // 1. GET ACTIVE & UPCOMING BOOKINGS (Full PII for Staff, Scrubbed for Public Availability Check)
    @GetMapping
    public List<Booking> getAllBookings(@RequestHeader(value = "X-Staff-Token", required = false) String token) {
        LocalDate today = LocalDate.now(LONDON_ZONE);

        if (staffController.isValidStaffToken(token)) {
            return bookingRepository.findByBookingDateGreaterThanEqualOrderByBookingDateAscBookingTimeAsc(today);
        }

        return bookingRepository.findPublicUpcomingBookings(today).stream()
                .map(this::maskPublicBooking)
                .toList();
    }

    // 2. GET PAST BOOKING HISTORY (STAFF ONLY)
    @GetMapping("/history")
    public ResponseEntity<?> getBookingHistory(@RequestHeader(value = "X-Staff-Token", required = false) String token) {
        if (!staffController.isValidStaffToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Unauthorized: Staff authentication required.");
        }
        LocalDate today = LocalDate.now(LONDON_ZONE);
        List<Booking> history = bookingRepository.findByBookingDateBeforeOrderByBookingDateDescBookingTimeAsc(today);
        return ResponseEntity.ok(history);
    }

    // 3. GET TODAY'S BOOKINGS (STAFF ONLY)
    @GetMapping("/today")
    public ResponseEntity<?> getTodaysBookings(@RequestHeader(value = "X-Staff-Token", required = false) String token) {
        if (!staffController.isValidStaffToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Unauthorized: Staff authentication required.");
        }
        LocalDate today = LocalDate.now(LONDON_ZONE);
        List<Booking> todays = bookingRepository.findByBookingDateOrderByBookingTimeAsc(today);
        return ResponseEntity.ok(todays);
    }

    // 4. CREATE A NEW BOOKING (Public Customer OR Authenticated Staff Override)
    @PostMapping
    public ResponseEntity<?> createBooking(
            @RequestHeader(value = "X-Staff-Token", required = false) String token,
            @RequestBody Booking booking) {

        boolean isStaffRequest = staffController.isValidStaffToken(token);

        if (booking.getBookingDate() == null || booking.getBookingTime() == null) {
            return ResponseEntity.badRequest().body("Booking date and time are required.");
        }
        if (booking.getCustomer() == null || booking.getCustomer().getFullName() == null
                || booking.getCustomer().getFullName().isBlank()) {
            return ResponseEntity.badRequest().body("Guest name is required.");
        }
        if (booking.getRestaurantTable() == null || booking.getRestaurantTable().getId() == null) {
            return ResponseEntity.badRequest().body("A valid table selection is required.");
        }

        // Validate Operating Days (Closed Tuesdays) & Hours
        DayOfWeek day = booking.getBookingDate().getDayOfWeek();
        if (day == DayOfWeek.TUESDAY) {
            return ResponseEntity.badRequest().body("We are closed on Tuesdays. Please select another day.");
        }

        LocalTime minTime = (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY)
                ? LocalTime.of(12, 0) : LocalTime.of(16, 0);
        LocalTime maxTime = (day == DayOfWeek.SUNDAY)
                ? LocalTime.of(20, 45) : LocalTime.of(21, 0);

        if (booking.getBookingTime().isBefore(minTime) || booking.getBookingTime().isAfter(maxTime)) {
            return ResponseEntity.badRequest()
                    .body("Reservations for this day must be between " + minTime + " and " + maxTime + ".");
        }

        Optional<RestaurantTable> tableOpt = tableRepository.findById(booking.getRestaurantTable().getId());
        if (tableOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("Selected table does not exist.");
        }
        RestaurantTable targetTable = tableOpt.get();
        int tableNum = targetTable.getTableNumber();
        int guests = booking.getPartySize();

        // Enforce Customer-Facing Table Yield Rules (Staff can override via Staff Modal)
        if (!isStaffRequest) {
            if (booking.getBookingDate().isBefore(LocalDate.now(LONDON_ZONE))) {
                return ResponseEntity.badRequest().body("Cannot book a date in the past.");
            }
            if (guests < 1 || guests > 20) {
                return ResponseEntity.badRequest().body("Online bookings are limited to 1-20 guests.");
            }
            if (tableNum == 4) {
                return ResponseEntity.badRequest().body("Table 4 is not available for online reservations.");
            }
            if (guests < 4 && (tableNum == 1 || tableNum == 5)) {
                return ResponseEntity.badRequest().body("Window Tables 1 and 5 require 4 or more guests.");
            }
            if (guests == 1 && tableNum != 8 && tableNum != 9 && tableNum != 10) {
                return ResponseEntity.badRequest().body("Solo dining reservations are allocated to Tables 8, 9, or 10.");
            }
            if (targetTable.getCapacity() < guests) {
                return ResponseEntity.badRequest().body("Selected table cannot accommodate this party size.");
            }
        }

        // Synchronized Critical Section: Prevent Double-Bookings & Phone Spam
        Booking savedBooking;
        synchronized (bookingLock) {
            List<Booking> sameDayBookings = bookingRepository.findActiveBookingsByDate(booking.getBookingDate());

            // Check 120-minute sitting overlap on the requested table
            boolean tableConflict = sameDayBookings.stream()
                    .filter(b -> b.getRestaurantTable() != null
                            && targetTable.getId().equals(b.getRestaurantTable().getId()))
                    .anyMatch(b -> isRangesOverlap(booking.getBookingTime(), b.getBookingTime()));

            if (tableConflict) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body("This table is already reserved within this 2-hour sitting window.");
            }

            // Server-Side Phone Anti-Spam Check for Public Customers
            if (!isStaffRequest && booking.getCustomer().getPhone() != null) {
                String cleanIncomingPhone = booking.getCustomer().getPhone().replaceAll("\\s+", "");
                if (!cleanIncomingPhone.isEmpty() && !"N/A".equalsIgnoreCase(cleanIncomingPhone)) {
                    boolean duplicatePhone = sameDayBookings.stream()
                            .anyMatch(b -> b.getCustomer() != null
                                    && b.getCustomer().getPhone() != null
                                    && b.getCustomer().getPhone().replaceAll("\\s+", "").equals(cleanIncomingPhone));
                    if (duplicatePhone) {
                        return ResponseEntity.badRequest().body(
                                "❌ Anti-Spam Protection: A reservation is already secured under this phone number for this date.");
                    }
                }
            }

            Customer savedCustomer = customerRepository.save(booking.getCustomer());
            booking.setCustomer(savedCustomer);
            booking.setRestaurantTable(targetTable);

            if (!isStaffRequest || booking.getStatus() == null || booking.getStatus().isBlank()) {
                booking.setStatus("CONFIRMED");
            }

            savedBooking = bookingRepository.save(booking);
        }

        // Send HTML Confirmation Email with MH-0000 Reference Code (if email provided)
        if (savedBooking.getCustomer() != null && savedBooking.getCustomer().getEmail() != null) {
            emailService.sendConfirmationEmail(
                    savedBooking.getCustomer().getEmail(),
                    savedBooking.getCustomer().getFullName(),
                    savedBooking.getBookingDate().toString(),
                    savedBooking.getBookingTime().toString(),
                    savedBooking.getPartySize(),
                    savedBooking.getId()
            );
        }

        return ResponseEntity.ok(savedBooking);
    }

    // 5. UPDATE BOOKING STATUS (ARRIVED / NO_SHOW) - STAFF ONLY
    @PutMapping("/{id}/status")
    @Transactional
    public ResponseEntity<?> updateBookingStatus(
            @RequestHeader(value = "X-Staff-Token", required = false) String token,
            @PathVariable Long id,
            @RequestBody Map<String, String> payload) {

        if (!staffController.isValidStaffToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        Optional<Booking> bookingOpt = bookingRepository.findById(id);
        if (bookingOpt.isEmpty()) return ResponseEntity.notFound().build();

        Booking booking = bookingOpt.get();
        booking.setStatus(payload.get("status"));
        return ResponseEntity.ok(bookingRepository.save(booking));
    }

    // 6. UPDATE STAFF INTERNAL NOTES - STAFF ONLY
    @PutMapping("/{id}/notes")
    @Transactional
    public ResponseEntity<?> updateStaffNotes(
            @RequestHeader(value = "X-Staff-Token", required = false) String token,
            @PathVariable Long id,
            @RequestBody Map<String, String> payload) {

        if (!staffController.isValidStaffToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        Optional<Booking> bookingOpt = bookingRepository.findById(id);
        if (bookingOpt.isEmpty()) return ResponseEntity.notFound().build();

        Booking booking = bookingOpt.get();
        booking.setStaffNotes(payload.get("notes"));
        return ResponseEntity.ok(bookingRepository.save(booking));
    }

    // 7. CHANGE SITTING TIME - STAFF ONLY
    @PutMapping("/{id}/time")
    @Transactional
    public ResponseEntity<?> updateBookingTime(
            @RequestHeader(value = "X-Staff-Token", required = false) String token,
            @PathVariable Long id,
            @RequestBody Map<String, String> payload) {

        if (!staffController.isValidStaffToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        Optional<Booking> bookingOpt = bookingRepository.findById(id);
        if (bookingOpt.isEmpty()) return ResponseEntity.notFound().build();

        String newTimeStr = payload.get("bookingTime");
        if (newTimeStr == null || newTimeStr.isBlank()) {
            return ResponseEntity.badRequest().body("Valid time required.");
        }
        if (newTimeStr.length() == 5) newTimeStr += ":00";

        Booking booking = bookingOpt.get();
        booking.setBookingTime(LocalTime.parse(newTimeStr));
        return ResponseEntity.ok(bookingRepository.save(booking));
    }

    // 8. REASSIGN TABLE - STAFF ONLY (Checks 2-Hour Sitting Conflict)
    @PutMapping("/{id}/table/{tableId}")
    @Transactional
    public ResponseEntity<?> reassignTable(
            @RequestHeader(value = "X-Staff-Token", required = false) String token,
            @PathVariable Long id,
            @PathVariable Long tableId) {

        if (!staffController.isValidStaffToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        Optional<Booking> bookingOpt = bookingRepository.findById(id);
        Optional<RestaurantTable> tableOpt = tableRepository.findById(tableId);

        if (bookingOpt.isEmpty() || tableOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("Booking or Table not found.");
        }

        Booking booking = bookingOpt.get();
        RestaurantTable newTable = tableOpt.get();

        boolean conflict = bookingRepository.findActiveBookingsByDate(booking.getBookingDate()).stream()
                .filter(b -> !b.getId().equals(booking.getId()))
                .filter(b -> b.getRestaurantTable() != null && newTable.getId().equals(b.getRestaurantTable().getId()))
                .anyMatch(b -> isRangesOverlap(booking.getBookingTime(), b.getBookingTime()));

        if (conflict) {
            return ResponseEntity.badRequest()
                    .body("Table " + newTable.getTableNumber() + " is already booked during that 2-hour window.");
        }

        booking.setRestaurantTable(newTable);
        return ResponseEntity.ok(bookingRepository.save(booking));
    }

    // 9. CANCEL BOOKING & NOTIFY GUEST - STAFF ONLY
    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancelBooking(
            @RequestHeader(value = "X-Staff-Token", required = false) String token,
            @PathVariable Long id,
            @RequestBody Map<String, String> payload) {

        if (!staffController.isValidStaffToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        Optional<Booking> bookingOpt = bookingRepository.findById(id);
        if (bookingOpt.isEmpty()) return ResponseEntity.notFound().build();

        Booking booking = bookingOpt.get();
        String reason = payload.getOrDefault("reason", "Cancelled by restaurant management");

        if (booking.getCustomer() != null && booking.getCustomer().getEmail() != null) {
            emailService.sendCancellationEmail(
                    booking.getCustomer().getEmail(),
                    booking.getCustomer().getFullName(),
                    booking.getBookingDate().toString(),
                    booking.getBookingTime().toString(),
                    reason
            );
        }

        bookingRepository.deleteById(id);
        return ResponseEntity.ok("Cancelled");
    }
}