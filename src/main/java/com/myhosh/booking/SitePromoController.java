package com.myhosh.booking;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/site-content")
public class SitePromoController {

    private final SitePromoConfigRepository repository;

    public SitePromoController(SitePromoConfigRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public SitePromoConfig getSiteContent() {
        return repository.findById(1L).orElseGet(SitePromoConfig::createDefault);
    }

    @PutMapping
    public ResponseEntity<?> updateSiteContent(
            @RequestHeader(value = "X-Staff-Token", required = false) String token,
            @RequestBody SitePromoConfig incoming) {

        if (!StaffController.isAdminToken(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Forbidden: Only ADMIN can update hero promotions or event posters.");
        }

        incoming.setId(1L);
        return ResponseEntity.ok(repository.save(incoming));
    }
}