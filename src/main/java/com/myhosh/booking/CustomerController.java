package com.myhosh.booking;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerRepository repository;

    public CustomerController(CustomerRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public ResponseEntity<?> getAllCustomers(
            @RequestHeader(value = "X-Staff-Token", required = false) String token) {
        if (!StaffController.isValidStaffToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        if (!StaffController.isAdminToken(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden: ADMIN access required.");
        }
        return ResponseEntity.ok(repository.findAll());
    }

    @PostMapping
    public ResponseEntity<?> createCustomer(
            @RequestHeader(value = "X-Staff-Token", required = false) String token,
            @RequestBody Customer newCustomer) {
        if (!StaffController.isValidStaffToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        if (!StaffController.isAdminToken(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden: ADMIN access required.");
        }
        return ResponseEntity.ok(repository.save(newCustomer));
    }
}