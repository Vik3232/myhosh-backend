package com.myhosh.booking;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/health")
    public ResponseEntity healthCheck() {
        try {
            jdbcTemplate.execute("SELECT 1");
            return ResponseEntity.ok("Render container and Neon DB pool are hot");
        } catch (Exception e) {
            System.err.println("Health check DB error: " + e.getMessage());
            return ResponseEntity.internalServerError().body("Database connection degraded");
        }
    }
}