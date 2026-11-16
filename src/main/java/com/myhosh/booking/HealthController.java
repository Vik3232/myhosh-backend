package com.myhosh.booking;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/health")
    public ResponseEntity> healthCheck() {
        Map<String, String> response = new HashMap<>();
        try {
            // Trivial query to force the Neon PostgreSQL pool to stay active
            jdbcTemplate.execute("SELECT 1");

            response.put("status", "active");
            response.put("message", "Render container and Neon DB pool are hot");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            // Log this securely to your server console/logs
            System.err.println("Health check DB error: " + e.getMessage());

            // Do not expose raw database error traces to the client
            response.put("status", "error");
            response.put("message", "Database connection degraded");
            return ResponseEntity.internalServerError().body(response);
        }
    }
}
