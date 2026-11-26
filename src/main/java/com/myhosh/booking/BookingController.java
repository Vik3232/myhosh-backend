package com.myhosh.booking;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
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
    private final GdprAuditLogRepository gdprAuditLogRepository;

    private final Object bookingLock = new Object();

    public BookingController(BookingRepository bookingRepository,
                             CustomerRepository customerRepository,
                             RestaurantTableRepository tableRepository,
                             EmailService emailService,
                             StaffController staffController,
                             GdprAuditLogRepository gdprAuditLogRepository) {
        this.bookingRepository = bookingRepository;
        this.customerRepository = customerRepository;
        this.tableRepository = tableRepository;
        this.emailService = emailService;
        this.staffController = staffController;
        this.gdprAuditLogRepository = gdprAuditLogRepository;
    }

    @Scheduled(initialDelay = 30000, fixedRate = 86400000)
    @Transactional
    public void purgeExpiredGdprBookings() {
        try {
            LocalDate cutoffDate = LocalDate.now(LONDON_ZONE).minusMonths(6);
            int deletedBookings = bookingRepository.deleteExpiredBookingsBefore(cutoffDate);
            int deletedCustomers = customerRepository.deleteOrphanedCustomers();
            System.out.println("[UK GDPR Auto-Purge] Deleted " + deletedBookings
                    + " reservations and " + deletedCustomers + " orphaned customer records before " + cutoffDate + ".");
        } catch (Exception e) {
            System.err.println("[UK GDPR Auto-Purge] Error during scheduled cleanup: " + e.getMessage());
        }
    }



    @Scheduled(initialDelay = 60000, fixedRate = 900000)
    @Transactional
    public void processPendingReviewEmails() {
        try {
            Instant now = Instant.now();
            Instant cutoff = now.minus(Duration.ofMinutes(210)); // 3.5 hours after marked COMPLETED
            Instant oldestAllowed = now.minus(Duration.ofHours(48));

            List<Booking> dueBookings = bookingRepository.findDueReviewEmails(cutoff, oldestAllowed);
            for (Booking booking : dueBookings) {
                booking.setReviewEmailSent(true);
                bookingRepository.save(booking);

                if (booking.getCustomer() != null && booking.getCustomer().getEmail() != null) {
                    emailService.sendReviewRequestEmail(
                            booking.getCustomer().getEmail(),
                            booking.getCustomer().getFullName()
                    );
                }
            }
        } catch (Exception e) {
            System.err.println("Error processing post-dining review emails: " + e.getMessage());
        }
    }

    private Booking maskPublicBooking(Booking b) {
        Booking masked = new Booking();
        masked.setId(b.getId());
        masked.setBookingDate(b.getBookingDate());
        masked.setBookingTime(b.getBookingTime());
        masked.setPartySize(b.getPartySize());
        masked.setStatus(b.getStatus());
        masked.setRestaurantTable(b.getRestaurantTable());
        return masked;
    }

    // Security (OWASP API3): Strip consent timestamps & internal keys for non-ADMIN floor staff using a detached copy
    private Booking maskForFloorStaff(Booking b) {
        Booking copy = new Booking();
        copy.setId(b.getId());
        copy.setBookingDate(b.getBookingDate());
        copy.setBookingTime(b.getBookingTime());
        copy.setPartySize(b.getPartySize());
        copy.setStatus(b.getStatus());
        copy.setSpecialRequests(b.getSpecialRequests());
        copy.setCustomer(b.getCustomer());
        copy.setRestaurantTable(b.getRestaurantTable());
        copy.setCompletedAt(b.getCompletedAt());
        return copy;
    }

    private boolean isRangesOverlap(LocalTime start1, LocalTime start2) {
        if (start1 == null || start2 == null) return false;
        LocalTime end1 = start1.plusMinutes(120);
        LocalTime end2 = start2.plusMinutes(120);
        return start1.isBefore(end2) && end1.isAfter(start2);
    }

    @GetMapping
    public List<Booking> getAllBookings(@RequestHeader(value = "X-Staff-Token", required = false) String token) {
        LocalDate today = LocalDate.now(LONDON_ZONE);
        if (StaffController.isValidStaffToken(token)) {
            List<Booking> bookings = bookingRepository.findByBookingDateGreaterThanEqualOrderByBookingDateAscBookingTimeAsc(today);
            if (StaffController.isAdminToken(token)) {
                return bookings;
            }
            return bookings.stream().map(this::maskForFloorStaff).toList();
        }
        return bookingRepository.findPublicUpcomingBookings(today).stream()
                .map(this::maskPublicBooking)
                .toList();
    }

    @GetMapping("/history")
    public ResponseEntity<?> getBookingHistory(@RequestHeader(value = "X-Staff-Token", required = false) String token) {
        if (!StaffController.isValidStaffToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized: Staff authentication required.");
        }
        LocalDate today = LocalDate.now(LONDON_ZONE);
        if (StaffController.isAdminToken(token)) {
            return ResponseEntity.ok(
                    bookingRepository.findByBookingDateBeforeOrderByBookingDateDescBookingTimeAsc(today)
            );
        }

        // Floor STAFF: rolling 15-day lookback only, with consent/internal fields stripped
        LocalDate fifteenDaysAgo = today.minusDays(15);
        List<Booking> recentHistory = bookingRepository
                .findByBookingDateGreaterThanEqualAndBookingDateBeforeOrderByBookingDateDescBookingTimeAsc(
                        fifteenDaysAgo,
                        today
                )
                .stream()
                .map(this::maskForFloorStaff)
                .toList();
        return ResponseEntity.ok(recentHistory);
    }

    @GetMapping("/today")
    public ResponseEntity<?> getTodaysBookings(@RequestHeader(value = "X-Staff-Token", required = false) String token) {
        if (!StaffController.isValidStaffToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized: Staff authentication required.");
        }
        LocalDate today = LocalDate.now(LONDON_ZONE);
        List<Booking> bookings = bookingRepository.findByBookingDateOrderByBookingTimeAsc(today);
        if (StaffController.isAdminToken(token)) {
            return ResponseEntity.ok(bookings);
        }
        return ResponseEntity.ok(bookings.stream().map(this::maskForFloorStaff).toList());
    }

    @PostMapping
    public ResponseEntity<?> createBooking(
            @RequestHeader(value = "X-Staff-Token", required = false) String token,
            @RequestBody Booking booking) {

        boolean isStaffRequest = StaffController.isValidStaffToken(token);

        if (booking.getBookingDate() == null || booking.getBookingTime() == null) {
            return ResponseEntity.badRequest().body("Booking date and time are required.");
        }
        if (booking.getCustomer() == null || booking.getCustomer().getFullName() == null
                || booking.getCustomer().getFullName().isBlank()) {
            return ResponseEntity.badRequest().body("Guest name is required.");
        }

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

        // Staff may leave table null for Custom Tables or "Table Not Sure Yet" (unlimited)
        RestaurantTable targetTable = null;
        if (booking.getRestaurantTable() != null && booking.getRestaurantTable().getId() != null) {
            Optional<RestaurantTable> tableOpt = tableRepository.findById(booking.getRestaurantTable().getId());
            if (tableOpt.isEmpty()) {
                return ResponseEntity.badRequest().body("Selected table does not exist.");
            }
            targetTable = tableOpt.get();
        } else if (!isStaffRequest) {
            return ResponseEntity.badRequest().body("A valid table selection is required.");
        }

        int guests = booking.getPartySize() != null ? booking.getPartySize() : 0;

        if (!isStaffRequest) {
            int tableNum = targetTable.getTableNumber();
            if (booking.getBookingDate().isBefore(LocalDate.now(LONDON_ZONE))) {
                return ResponseEntity.badRequest().body("Cannot book a date in the past.");
            }
            if (guests < 1 || guests > 20) {
                return ResponseEntity.badRequest().body("Online bookings are limited to 1-20 guests.");
            }
            if (guests < 4 && (tableNum == 1 || tableNum == 5)) {
                return ResponseEntity.badRequest().body("Window Tables 1 and 5 require 4 or more guests.");
            }
            if (guests == 1 && tableNum != 8 && tableNum != 9 && tableNum != 10 && tableNum != 4) {
                return ResponseEntity.badRequest().body("Solo dining reservations are allocated to Tables 8, 9, 10 (or Table 4 when busy).");
            }
            if (targetTable.getCapacity() < guests) {
                return ResponseEntity.badRequest().body("Selected table cannot accommodate this party size.");
            }
        }

        String cleanIdempotencyKey = (booking.getIdempotencyKey() != null && !booking.getIdempotencyKey().isBlank())
                ? booking.getIdempotencyKey().trim()
                : null;
        booking.setIdempotencyKey(cleanIdempotencyKey);

        Booking savedBooking;
        final RestaurantTable lockedTable = targetTable;
        synchronized (bookingLock) {
            if (cleanIdempotencyKey != null) {
                Optional<Booking> existingBooking = bookingRepository.findByIdempotencyKey(cleanIdempotencyKey);
                if (existingBooking.isPresent()) {
                    return ResponseEntity.ok(existingBooking.get());
                }
            }

            List<Booking> sameDayBookings = bookingRepository.findActiveBookingsByDate(booking.getBookingDate());

            if (lockedTable != null) {
                boolean tableConflict = sameDayBookings.stream()
                        .filter(b -> b.getRestaurantTable() != null
                                && lockedTable.getId().equals(b.getRestaurantTable().getId()))
                        .anyMatch(b -> isRangesOverlap(booking.getBookingTime(), b.getBookingTime()));

                if (tableConflict) {
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                            .body("This table is already reserved within this 2-hour sitting window.");
                }
            }

            if (!isStaffRequest && booking.getCustomer().getPhone() != null) {
                String cleanIncomingPhone = booking.getCustomer().getPhone().replaceAll("\\s+", "");
                if (!cleanIncomingPhone.isEmpty() && !"N/A".equalsIgnoreCase(cleanIncomingPhone)) {
                    boolean duplicatePhone = sameDayBookings.stream()
                            .anyMatch(b -> b.getCustomer() != null
                                    && b.getCustomer().getPhone() != null
                                    && b.getCustomer().getPhone().replaceAll("\\s+", "").equals(cleanIncomingPhone));
                    if (duplicatePhone) {
                        return ResponseEntity.badRequest().body(
                                "Anti-Spam Protection: A reservation is already secured under this phone number for this date.");
                    }
                }
            }

            Customer savedCustomer = customerRepository.save(booking.getCustomer());
            booking.setCustomer(savedCustomer);
            booking.setRestaurantTable(lockedTable);

            if (!isStaffRequest || booking.getStatus() == null || booking.getStatus().isBlank()) {
                booking.setStatus("CONFIRMED");
            }

            // UK GDPR Art. 7: Record explicit consent + timestamp separately for review vs. marketing
            String guestEmail = booking.getCustomer().getEmail();
            boolean hasValidEmail = guestEmail != null
                    && !guestEmail.isBlank()
                    && guestEmail.contains("@")
                    && !guestEmail.trim().toLowerCase().endsWith(".local");

            if (!isStaffRequest && hasValidEmail && Boolean.TRUE.equals(booking.getReviewConsent())) {
                booking.setReviewConsent(true);
                booking.setReviewConsentAt(Instant.now());
            } else {
                booking.setReviewConsent(false);
                booking.setReviewConsentAt(null);
            }

            if (!isStaffRequest && hasValidEmail && Boolean.TRUE.equals(booking.getMarketingConsent())) {
                booking.setMarketingConsent(true);
                booking.setMarketingConsentAt(Instant.now());
            } else {
                booking.setMarketingConsent(false);
                booking.setMarketingConsentAt(null);
            }

            booking.setReviewEmailSent(false);

            savedBooking = bookingRepository.save(booking);
        }

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

    @PutMapping("/{id}/status")
    @Transactional
    public ResponseEntity<?> updateBookingStatus(
            @RequestHeader(value = "X-Staff-Token", required = false) String token,
            @PathVariable Long id,
            @RequestBody Map<String, String> payload) {

        if (!StaffController.isValidStaffToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        Optional<Booking> bookingOpt = bookingRepository.findById(id);
        if (bookingOpt.isEmpty()) return ResponseEntity.notFound().build();

        Booking booking = bookingOpt.get();
        String newStatus = payload.get("status");
        booking.setStatus(newStatus);

        if ("COMPLETED".equalsIgnoreCase(newStatus)) {
            if (booking.getCompletedAt() == null) {
                booking.setCompletedAt(Instant.now());
            }
            if (booking.getReviewEmailSent() == null) {
                booking.setReviewEmailSent(false);
            }
        } else if (!Boolean.TRUE.equals(booking.getReviewEmailSent())) {
            booking.setCompletedAt(null);
        }


        return ResponseEntity.ok(bookingRepository.save(booking));
    }

    @PutMapping("/{id}/review-consent")
    @Transactional
    public ResponseEntity<?> updateReviewConsent(
            @RequestHeader(value = "X-Staff-Token", required = false) String token,
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> payload) {

        if (!StaffController.isValidStaffToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        Optional<Booking> bookingOpt = bookingRepository.findById(id);
        if (bookingOpt.isEmpty()) return ResponseEntity.notFound().build();

        Booking booking = bookingOpt.get();
        boolean consent = Boolean.TRUE.equals(payload.get("reviewConsent"));
        booking.setReviewConsent(consent);
        booking.setReviewConsentAt(consent ? Instant.now() : null);
        return ResponseEntity.ok(bookingRepository.save(booking));
    }

    @PutMapping("/{id}/marketing-consent")
    @Transactional
    public ResponseEntity<?> updateMarketingConsent(
            @RequestHeader(value = "X-Staff-Token", required = false) String token,
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> payload) {

        if (!StaffController.isValidStaffToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        if (!StaffController.isAdminToken(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Forbidden: ADMIN access required to update marketing consent.");
        }
        Optional<Booking> bookingOpt = bookingRepository.findById(id);
        if (bookingOpt.isEmpty()) return ResponseEntity.notFound().build();

        Booking booking = bookingOpt.get();
        boolean consent = Boolean.TRUE.equals(payload.get("marketingConsent"));
        booking.setMarketingConsent(consent);
        booking.setMarketingConsentAt(consent ? Instant.now() : null);
        return ResponseEntity.ok(bookingRepository.save(booking));
    }

    // UK GDPR Art. 5(1)(c) Data Minimisation: Exports ONLY opted-in Name, Email, and Consent Timestamp
    @GetMapping("/marketing-export")
    @Transactional
    public ResponseEntity<?> exportMarketingOptIns(
            @RequestHeader(value = "X-Staff-Token", required = false) String token) {

        if (!StaffController.isValidStaffToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }

        List<Booking> optedIn = bookingRepository.findMarketingOptInBookings();

        // Deduplicate by lowercase email, keeping each guest's most recent consent record
        Map<String, Booking> uniqueByEmail = new LinkedHashMap<>();
        for (Booking b : optedIn) {
            if (b.getCustomer() == null || b.getCustomer().getEmail() == null) continue;
            String normalizedEmail = b.getCustomer().getEmail().trim().toLowerCase();
            if (normalizedEmail.isEmpty() || !normalizedEmail.contains("@") || normalizedEmail.endsWith(".local")) {
                continue;
            }
            uniqueByEmail.putIfAbsent(normalizedEmail, b);
        }

        StringBuilder csv = new StringBuilder();
        csv.append("Full Name,Email,Consent Timestamp (UTC)\n");
        for (Booking b : uniqueByEmail.values()) {
            String name = b.getCustomer().getFullName();
            String email = b.getCustomer().getEmail().trim();
            String consentedAt = b.getMarketingConsentAt() != null ? b.getMarketingConsentAt().toString() : "";
            csv.append(sanitizeCsvCell(name)).append(',')
                    .append(sanitizeCsvCell(email)).append(',')
                    .append(sanitizeCsvCell(consentedAt)).append('\n');
        }

        // Audit every bulk marketing export
        GdprAuditLog audit = new GdprAuditLog();
        audit.setBookingId(0L);
        audit.setAction("MARKETING_CSV_EXPORT");
        audit.setPerformedBy(StaffController.getSessionUsername(token));
        audit.setActorRole(StaffController.getSessionRole(token));
        audit.setPerformedAt(Instant.now());
        gdprAuditLogRepository.save(audit);

        String filename = "myhosh-marketing-optins-" + LocalDate.now(LONDON_ZONE) + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    // OWASP CSV Injection Defense: Prevents Excel formula execution (=, +, -, @) in guest names
    private String sanitizeCsvCell(String value) {
        if (value == null) return "\"\"";
        String clean = value.replace("\r", " ").replace("\n", " ").trim();
        if (!clean.isEmpty()) {
            char first = clean.charAt(0);
            if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t') {
                clean = "'" + clean;
            }
        }
        return "\"" + clean.replace("\"", "\"\"") + "\"";
    }

    @PutMapping("/{id}/notes")
    @Transactional
    public ResponseEntity<?> updateStaffNotes(
            @RequestHeader(value = "X-Staff-Token", required = false) String token,
            @PathVariable Long id,
            @RequestBody Map<String, String> payload) {

        if (!StaffController.isValidStaffToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        Optional<Booking> bookingOpt = bookingRepository.findById(id);
        if (bookingOpt.isEmpty()) return ResponseEntity.notFound().build();

        Booking booking = bookingOpt.get();
        booking.setStaffNotes(payload.get("notes"));
        return ResponseEntity.ok(bookingRepository.save(booking));
    }

    // UPDATE SITTING TIME - STAFF ONLY
    @PutMapping("/{id}/time")
    @Transactional
    public ResponseEntity<?> updateBookingTime(
            @RequestHeader(value = "X-Staff-Token", required = false) String token,
            @PathVariable Long id,
            @RequestBody Map<String, String> payload) {

        if (!StaffController.isValidStaffToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        Optional<Booking> bookingOpt = bookingRepository.findById(id);
        if (bookingOpt.isEmpty()) return ResponseEntity.notFound().build();

        String newTimeStr = payload.get("bookingTime");
        if (newTimeStr == null || newTimeStr.isBlank()) {
            newTimeStr = payload.get("time");
        }
        if (newTimeStr == null || newTimeStr.isBlank()) {
            return ResponseEntity.badRequest().body("Valid time required.");
        }

        newTimeStr = newTimeStr.trim();
        if (newTimeStr.length() == 5) {
            newTimeStr += ":00";
        }

        try {
            Booking booking = bookingOpt.get();
            booking.setBookingTime(LocalTime.parse(newTimeStr));
            return ResponseEntity.ok(bookingRepository.save(booking));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body("Invalid time format. Use HH:mm (e.g. 19:30).");
        }
    }

    // UPDATE PARTY SIZE (GUEST COUNT) - STAFF ONLY
    @PutMapping("/{id}/party-size")
    @Transactional
    public ResponseEntity<?> updatePartySize(
            @RequestHeader(value = "X-Staff-Token", required = false) String token,
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload) {

        if (!StaffController.isValidStaffToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        Optional<Booking> bookingOpt = bookingRepository.findById(id);
        if (bookingOpt.isEmpty()) return ResponseEntity.notFound().build();

        try {
            int newSize = Integer.parseInt(String.valueOf(payload.get("partySize")));
            if (newSize < 1 || newSize > 50) {
                return ResponseEntity.badRequest().body("Party size must be between 1 and 50 guests.");
            }
            Booking booking = bookingOpt.get();
            booking.setPartySize(newSize);
            return ResponseEntity.ok(bookingRepository.save(booking));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body("Invalid guest number.");
        }
    }

    @PutMapping("/{id}/table/{tableId}")
    @Transactional
    public ResponseEntity<?> reassignTable(
            @RequestHeader(value = "X-Staff-Token", required = false) String token,
            @PathVariable Long id,
            @PathVariable Long tableId) {

        if (!StaffController.isValidStaffToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        Optional<Booking> bookingOpt = bookingRepository.findById(id);
        if (bookingOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("Booking not found.");
        }

        Booking booking = bookingOpt.get();

        // tableId = 0 moves booking to Custom / Table Not Sure Yet (no conflict check)
        if (tableId == 0L) {
            booking.setRestaurantTable(null);
            return ResponseEntity.ok(bookingRepository.save(booking));
        }

        Optional<RestaurantTable> tableOpt = tableRepository.findById(tableId);
        if (tableOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("Table not found.");
        }

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

    @PostMapping("/{id}/cancel")
    @Transactional
    public ResponseEntity<?> cancelBooking(
            @RequestHeader(value = "X-Staff-Token", required = false) String token,
            @PathVariable Long id,
            @RequestBody Map<String, String> payload) {

        if (!StaffController.isValidStaffToken(token)) {
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

        Long customerId = booking.getCustomer() != null ? booking.getCustomer().getId() : null;
        bookingRepository.delete(booking);
        bookingRepository.flush();

        if (customerId != null && !bookingRepository.existsByCustomerId(customerId) && customerRepository.existsById(customerId)) {
            customerRepository.deleteById(customerId);
        }

        return ResponseEntity.ok("Cancelled");
    }

    @PutMapping("/{id}/withdraw-email-consent")
    @Transactional
    public ResponseEntity<?> withdrawEmailConsent(
            @RequestHeader(value = "X-Staff-Token", required = false) String token,
            @PathVariable Long id) {

        if (!StaffController.isValidStaffToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        if (!StaffController.isAdminToken(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Forbidden: ADMIN access required to manage email consent.");
        }

        Optional<Booking> bookingOpt = bookingRepository.findById(id);
        if (bookingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Booking booking = bookingOpt.get();
        String email = (booking.getCustomer() != null && booking.getCustomer().getEmail() != null)
                ? booking.getCustomer().getEmail().trim()
                : "";

        int updatedCount;
        if (!email.isEmpty()) {
            updatedCount = bookingRepository.withdrawAllEmailConsentByEmail(email);
        } else {
            booking.setReviewConsent(false);
            booking.setReviewConsentAt(null);
            booking.setMarketingConsent(false);
            booking.setMarketingConsentAt(null);
            bookingRepository.save(booking);
            updatedCount = 1;
        }

        return ResponseEntity.ok(Map.of(
                "updatedCount", updatedCount,
                "email", email
        ));
    }

    @DeleteMapping("/{id}/customer-data")
    @Transactional
    public ResponseEntity<?> deleteCustomerData(
            @RequestHeader(value = "X-Staff-Token", required = false) String token,
            @PathVariable Long id) {

        if (!StaffController.isValidStaffToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        if (!StaffController.isAdminToken(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Forbidden: ADMIN access required to permanently erase customer data.");
        }

        Optional<Booking> bookingOpt = bookingRepository.findById(id);
        if (bookingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Booking booking = bookingOpt.get();
        Customer linkedCustomer = booking.getCustomer();
        Long customerId = linkedCustomer != null ? linkedCustomer.getId() : null;

        booking.setCustomer(null);
        booking.setSpecialRequests(null);
        booking.setStaffNotes(null);
        bookingRepository.save(booking);
        bookingRepository.delete(booking);
        bookingRepository.flush();

        if (customerId != null && !bookingRepository.existsByCustomerId(customerId) && customerRepository.existsById(customerId)) {
            customerRepository.deleteById(customerId);
        }

        GdprAuditLog audit = new GdprAuditLog();
        audit.setBookingId(id);
        audit.setAction("IMMEDIATE_CUSTOMER_ERASURE");
        audit.setPerformedBy(StaffController.getSessionUsername(token));
        audit.setActorRole(StaffController.getSessionRole(token));
        audit.setPerformedAt(Instant.now());
        gdprAuditLogRepository.save(audit);

        return ResponseEntity.ok("Customer personal data permanently deleted.");
    }
}