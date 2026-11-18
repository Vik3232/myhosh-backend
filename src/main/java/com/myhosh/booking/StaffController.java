package com.myhosh.booking;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/staff")
public class StaffController {

    private final StaffMemberRepository staffRepository;

    // Thread-safe active session store: token -> StaffSession
    private static final Map<String, StaffSession> ACTIVE_TOKENS = new ConcurrentHashMap<>();
    private static final long TOKEN_TTL_MS = 12 * 60 * 60 * 1000L; // 12-hour shift expiry

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

    // --- ZERO-TRUST TOKEN HELPERS (Used by BookingController & StaffController) ---
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
        if (token != null) {
            ACTIVE_TOKENS.remove(token.trim());
        }
    }

    // --- BUILT-IN SALTED SHA-256 PASSWORD HASHING (No pom.xml changes needed) ---
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
                String salt = parts[1];
                String expectedHash = hashPassword(rawPassword, salt);
                return MessageDigest.isEqual(
                        expectedHash.getBytes(StandardCharsets.UTF_8),
                        storedPassword.getBytes(StandardCharsets.UTF_8)
                );
            }
            return false;
        }
        // Legacy fallback for existing plain-text accounts in DB
        return storedPassword.equals(rawPassword);
    }

    // 1. Register a New Staff Member (Strictly ADMIN-Only unless bootstrapping first user)
    @PostMapping("/register")
    public ResponseEntity<?> registerStaff(
            @RequestHeader(value = "X-Staff-Token", required = false) String token,
            @RequestBody StaffMember newStaff
    ) {
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
        if (newStaff.getRole() == null || newStaff.getRole().trim().isEmpty()) {
            newStaff.setRole("STAFF");
        } else {
            newStaff.setRole(newStaff.getRole().trim().toUpperCase());
        }

        // Hash password before saving to Neon PostgreSQL
        String salt = generateSalt();
        newStaff.setPassword(hashPassword(newStaff.getPassword(), salt));

        StaffMember savedStaff = staffRepository.save(newStaff);
        return ResponseEntity.ok(savedStaff);
    }

    // 2. Check Login Credentials & Issue Session Token
    @PostMapping("/login")
    public ResponseEntity<?> loginStaff(@RequestBody Map<String, String> credentials) {
        String username = credentials.get("username");
        String password = credentials.get("password");

        if (username == null || password == null) {
            return ResponseEntity.status(400).body("Username and password are required.");
        }

        StaffMember staff = staffRepository.findByUsername(username.trim());

        if (staff != null && verifyPassword(password, staff.getPassword())) {
            // Transparently upgrade legacy plain-text password to salted SHA-256 hash on login
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
            return ResponseEntity.status(401).body("Invalid username or password.");
        }
    }

    // 3. Explicit Logout Endpoint to Revoke Server Token
    @PostMapping("/logout")
    public ResponseEntity<?> logoutStaff(@RequestHeader(value = "X-Staff-Token", required = false) String token) {
        revokeToken(token);
        return ResponseEntity.ok("Logged out securely.");
    }
}