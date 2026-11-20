package com.myhosh.booking;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/staff")
public class StaffController {

    private final StaffMemberRepository staffRepository;

    @Value("${STAFF_GATE_PIN:}")
    private String staffGatePin;

    private static final Map<String, StaffSession> ACTIVE_TOKENS = new ConcurrentHashMap<>();
    private static final long TOKEN_TTL_MS = 12 * 60 * 60 * 1000L;

    // Brute-force protection: max 5 failed logins per 15 minutes
    private static final Map<String, LoginAttempt> FAILED_LOGINS = new ConcurrentHashMap<>();
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCKOUT_MS = 15 * 60 * 1000L;

    private static class LoginAttempt {
        int count;
        long lockedUntil;
    }

    private static class StaffSession {
        final String username;
        final String role;
        final long expiresAt;

        StaffSession(String username, String role, long expiresAt) {
            this.username = username;
            this.role = role;
            this.expiresAt = expiresAt;
        }
    }

    public StaffController(StaffMemberRepository staffRepository) {
        this.staffRepository = staffRepository;
    }

    public static boolean isValidStaffToken(String token) {
        if (token == null || token.trim().isEmpty()) return false;
        StaffSession session = ACTIVE_TOKENS.get(token.trim());
        if (session == null) return false;
        if (System.currentTimeMillis() > session.expiresAt) {
            ACTIVE_TOKENS.remove(token.trim());
            return false;
        }
        return true;
    }

    public static boolean isAdminToken(String token) {
        if (!isValidStaffToken(token)) return false;
        StaffSession session = ACTIVE_TOKENS.get(token.trim());
        return session != null && "ADMIN".equalsIgnoreCase(session.role);
    }

    public static void revokeToken(String token) {
        if (token != null) ACTIVE_TOKENS.remove(token.trim());
    }

    public static void revokeTokensForUsername(String username) {
        if (username == null) return;
        String target = username.trim();
        ACTIVE_TOKENS.entrySet().removeIf(entry -> target.equalsIgnoreCase(entry.getValue().username));
    }

    public static String getSessionUsername(String token) {
        if (!isValidStaffToken(token)) return "UNKNOWN";
        StaffSession session = ACTIVE_TOKENS.get(token.trim());
        return session != null ? session.username : "UNKNOWN";
    }

    public static String getSessionRole(String token) {
        if (!isValidStaffToken(token)) return "NONE";
        StaffSession session = ACTIVE_TOKENS.get(token.trim());
        return session != null ? session.role : "NONE";
    }

    private String hashPassword(String rawPassword, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest((salt + ":" + rawPassword).getBytes(StandardCharsets.UTF_8));
            return "SHA256$" + salt + "$" + Base64.getEncoder().encodeToString(encoded);
        } catch (Exception e) {
            throw new RuntimeException("Error hashing password", e);
        }
    }

    private String generateSalt() {
        byte[] saltBytes = new byte[16];
        new SecureRandom().nextBytes(saltBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(saltBytes);
    }

    private boolean verifyPassword(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null) return false;
        if (storedPassword.startsWith("SHA256$")) {
            String[] parts = storedPassword.split("\\$");
            if (parts.length == 3) {
                String expectedHash = hashPassword(rawPassword, parts[1]);
                return MessageDigest.isEqual(
                        expectedHash.getBytes(StandardCharsets.UTF_8),
                        storedPassword.getBytes(StandardCharsets.UTF_8)
                );
            }
            return false;
        }
        return storedPassword.equals(rawPassword);
    }

    private void recordFailedAttempt(String key) {
        LoginAttempt attempt = FAILED_LOGINS.computeIfAbsent(key, k -> new LoginAttempt());
        attempt.count++;
        if (attempt.count >= MAX_FAILED_ATTEMPTS) {
            attempt.lockedUntil = System.currentTimeMillis() + LOCKOUT_MS;
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerStaff(
            @RequestHeader(value = "X-Staff-Token", required = false) String token,
            @RequestBody StaffMember newStaff) {

        long existingStaffCount = staffRepository.count();
        if (existingStaffCount > 0 && !isAdminToken(token)) {
            return ResponseEntity.status(403).body("Forbidden: Only an authenticated ADMIN can register new staff.");
        }

        if (newStaff.getUsername() == null || newStaff.getUsername().trim().isEmpty()
                || newStaff.getPassword() == null || newStaff.getPassword().length() < 6) {
            return ResponseEntity.badRequest().body("Username is required and password must be at least 6 characters.");
        }

        String cleanUsername = newStaff.getUsername().trim();
        if (staffRepository.findByUsername(cleanUsername) != null) {
            return ResponseEntity.badRequest().body("Username already exists.");
        }

        newStaff.setUsername(cleanUsername);
        newStaff.setRole((newStaff.getRole() == null || newStaff.getRole().trim().isEmpty())
                ? "STAFF" : newStaff.getRole().trim().toUpperCase());

        String salt = generateSalt();
        newStaff.setPassword(hashPassword(newStaff.getPassword(), salt));

        return ResponseEntity.ok(staffRepository.save(newStaff));
    }

    @PostMapping("/login")
    public ResponseEntity<?> loginStaff(@RequestBody Map<String, String> credentials) {
        String username = credentials.get("username");
        String password = credentials.get("password");
        String gatePin = credentials.get("gatePin");

        if (username == null || password == null) {
            return ResponseEntity.status(400).body("Username and password are required.");
        }

        String attemptKey = username.trim().toLowerCase();
        LoginAttempt attempt = FAILED_LOGINS.get(attemptKey);
        if (attempt != null && attempt.lockedUntil > System.currentTimeMillis()) {
            long minsLeft = Math.max(1, (attempt.lockedUntil - System.currentTimeMillis()) / 60000);
            return ResponseEntity.status(429).body("Too many failed attempts. Try again in " + minsLeft + " minute(s).");
        }

        // Second Security Layer: Server-side Staff Access PIN (when STAFF_GATE_PIN env var is set on Render)
        if (staffGatePin != null && !staffGatePin.isBlank()) {
            boolean pinMatch = gatePin != null && MessageDigest.isEqual(
                    gatePin.trim().getBytes(StandardCharsets.UTF_8),
                    staffGatePin.trim().getBytes(StandardCharsets.UTF_8)
            );
            if (!pinMatch) {
                recordFailedAttempt(attemptKey);
                return ResponseEntity.status(401).body("Invalid Staff Access PIN or credentials.");
            }
        }

        StaffMember staff = staffRepository.findByUsername(username.trim());
        if (staff != null && verifyPassword(password, staff.getPassword())) {
            FAILED_LOGINS.remove(attemptKey);

            if (!staff.getPassword().startsWith("SHA256$")) {
                String salt = generateSalt();
                staff.setPassword(hashPassword(password, salt));
                staffRepository.save(staff);
            }

            String sessionToken = UUID.randomUUID().toString() + "-" + UUID.randomUUID().toString();
            ACTIVE_TOKENS.put(sessionToken, new StaffSession(
                    staff.getUsername(),
                    staff.getRole(),
                    System.currentTimeMillis() + TOKEN_TTL_MS
            ));

            staff.setToken(sessionToken);
            return ResponseEntity.ok(staff);
        } else {
            recordFailedAttempt(attemptKey);
            return ResponseEntity.status(401).body("Invalid Staff Access PIN or credentials.");
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logoutStaff(@RequestHeader(value = "X-Staff-Token", required = false) String token) {
        revokeToken(token);
        return ResponseEntity.ok("Logged out securely.");
    }

    @GetMapping
    public ResponseEntity<?> listStaff(@RequestHeader(value = "X-Staff-Token", required = false) String token) {
        if (!isAdminToken(token)) return ResponseEntity.status(403).body("Forbidden: ADMIN access required.");
        return ResponseEntity.ok(staffRepository.findAll());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteStaff(
            @RequestHeader(value = "X-Staff-Token", required = false) String token,
            @PathVariable Long id) {

        if (!isAdminToken(token)) return ResponseEntity.status(403).body("Forbidden: ADMIN access required.");

        Optional<StaffMember> targetOpt = staffRepository.findById(id);
        if (targetOpt.isEmpty()) return ResponseEntity.notFound().build();

        StaffMember target = targetOpt.get();
        if (target.getUsername() != null && target.getUsername().equalsIgnoreCase(getSessionUsername(token))) {
            return ResponseEntity.badRequest().body("You cannot delete your own active admin account.");
        }

        if ("ADMIN".equalsIgnoreCase(target.getRole())) {
            long adminCount = staffRepository.findAll().stream()
                    .filter(s -> "ADMIN".equalsIgnoreCase(s.getRole())).count();
            if (adminCount <= 1) return ResponseEntity.badRequest().body("Cannot delete the last remaining ADMIN account.");
        }

        revokeTokensForUsername(target.getUsername());
        staffRepository.deleteById(id);
        return ResponseEntity.ok("Staff account removed and active sessions revoked.");
    }
}