package com.myhosh.booking;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/staff")
public class StaffController {

    private final StaffMemberRepository staffRepository;

    public StaffController(StaffMemberRepository staffRepository) {
        this.staffRepository = staffRepository;
    }

    // 1. Endpoint to Register a New Staff Member (Admin Only later)
    @PostMapping("/register")
    public ResponseEntity<?> registerStaff(@RequestBody StaffMember newStaff) {
        if (staffRepository.findByUsername(newStaff.getUsername()) != null) {
            return ResponseEntity.badRequest().body("Username already exists.");
        }

        // Default role if not provided
        if (newStaff.getRole() == null || newStaff.getRole().isEmpty()) {
            newStaff.setRole("STAFF");
        }

        StaffMember savedStaff = staffRepository.save(newStaff);
        return ResponseEntity.ok(savedStaff);
    }

    // 2. Endpoint to Check Login Credentials
    @PostMapping("/login")
    public ResponseEntity<?> loginStaff(@RequestBody Map<String, String> credentials) {
        String username = credentials.get("username");
        String password = credentials.get("password");

        StaffMember staff = staffRepository.findByUsername(username);

        if (staff != null && staff.getPassword().equals(password)) {
            // Success: send back the user details (without password ideally, but for now this works)
            return ResponseEntity.ok(staff);
        } else {
            return ResponseEntity.status(401).body("Invalid username or password.");
        }
    }
}
