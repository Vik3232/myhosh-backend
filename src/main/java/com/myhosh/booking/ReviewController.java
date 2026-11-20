package com.myhosh.booking;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final SiteReviewRepository reviewRepository;
    private final RestClient restClient = RestClient.create();

    @Value("${GOOGLE_PLACES_API_KEY:}")
    private String googleApiKey;

    @Value("${GOOGLE_PLACE_ID:}")
    private String googlePlaceId;

    private volatile Map<String, Object> cachedGoogleData = null;
    private volatile long cacheExpiresAt = 0L;
    private static final long CACHE_TTL_MS = 6 * 60 * 60 * 1000L; // 6 hours

    public ReviewController(SiteReviewRepository reviewRepository) {
        this.reviewRepository = reviewRepository;
    }

    private String asText(Object value, String fallback) {
        if (value == null) return fallback;
        String text = value.toString().trim();
        return text.isEmpty() ? fallback : text;
    }

    private Map<String, Object> fetchGoogleSummary() {
        long now = System.currentTimeMillis();
        if (cachedGoogleData != null && now < cacheExpiresAt) {
            return cachedGoogleData;
        }

        String fallbackMapsUri = "https://www.google.com/maps/search/?api=1&query=MyHosh+99+Fisherton+Street+Salisbury+SP2+7SP";
        Map<String, Object> result = new LinkedHashMap<>();

        if (googleApiKey == null || googleApiKey.isBlank() || googlePlaceId == null || googlePlaceId.isBlank()) {
            result.put("configured", false);
            result.put("rating", null);
            result.put("totalReviews", 0);
            result.put("googleMapsUri", fallbackMapsUri);
            result.put("reviews", Collections.emptyList());
            return result;
        }

        try {
            String url = "https://places.googleapis.com/v1/places/" + googlePlaceId.trim();
            Map<?, ?> root = restClient.get()
                    .uri(url)
                    .header("X-Goog-Api-Key", googleApiKey.trim())
                    .header("X-Goog-FieldMask", "displayName,rating,userRatingCount,reviews,googleMapsUri")
                    .retrieve()
                    .body(Map.class);

            if (root != null) {
                List<Object> parsedReviews = new ArrayList<>();
                Object rawReviews = root.get("reviews");

                if (rawReviews instanceof List<?> reviewList) {
                    for (Object entry : reviewList) {
                        if (entry instanceof Map<?, ?> rev) {
                            Map<String, Object> item = new LinkedHashMap<>();

                            String authorName = "Google Guest";
                            Object attr = rev.get("authorAttribution");
                            if (attr instanceof Map<?, ?> attrMap) {
                                authorName = asText(attrMap.get("displayName"), "Google Guest");
                            }

                            int rating = 5;
                            Object ratingObj = rev.get("rating");
                            if (ratingObj instanceof Number n) {
                                rating = n.intValue();
                            }

                            String reviewText = "";
                            Object textObj = rev.get("text");
                            if (textObj instanceof Map<?, ?> textMap) {
                                reviewText = asText(textMap.get("text"), "");
                            }

                            item.put("authorName", authorName);
                            item.put("rating", rating);
                            item.put("relativeTime", asText(rev.get("relativePublishTimeDescription"), ""));
                            item.put("text", reviewText);
                            parsedReviews.add(item);
                        }
                    }
                }

                double overallRating = root.get("rating") instanceof Number n ? n.doubleValue() : 0.0;
                int totalReviews = root.get("userRatingCount") instanceof Number n ? n.intValue() : 0;

                result.put("configured", true);
                result.put("rating", overallRating);
                result.put("totalReviews", totalReviews);
                result.put("googleMapsUri", asText(root.get("googleMapsUri"), fallbackMapsUri));
                result.put("reviews", parsedReviews);

                cachedGoogleData = result;
                cacheExpiresAt = now + CACHE_TTL_MS;
                return result;
            }
        } catch (Exception ex) {
            System.err.println("⚠️ [Google Reviews] Fetch failed: " + ex.getMessage());
        }

        result.put("configured", false);
        result.put("rating", null);
        result.put("totalReviews", 0);
        result.put("googleMapsUri", fallbackMapsUri);
        result.put("reviews", Collections.emptyList());
        return result;
    }

    @GetMapping
    public Map<String, Object> getAllReviews() {
        List<SiteReview> siteReviews = reviewRepository.findAllByOrderByCreatedAtDesc();
        double siteAvg = siteReviews.isEmpty() ? 0.0
                : Math.round(siteReviews.stream().mapToInt(SiteReview::getRating).average().orElse(0.0) * 10.0) / 10.0;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("google", fetchGoogleSummary());
        payload.put("siteAverageRating", siteAvg);
        payload.put("siteReviewCount", siteReviews.size());
        payload.put("websiteReviews", siteReviews);
        return payload;
    }

    @PostMapping
    public ResponseEntity<?> createWebsiteReview(@RequestBody SiteReview review) {
        if (review.getGuestName() == null || review.getGuestName().trim().length() < 2) {
            return ResponseEntity.badRequest().body("Please enter your name.");
        }
        if (review.getRating() < 1 || review.getRating() > 5) {
            return ResponseEntity.badRequest().body("Rating must be between 1 and 5 stars.");
        }
        if (review.getComment() == null || review.getComment().trim().length() < 8) {
            return ResponseEntity.badRequest().body("Please write at least a short sentence about your visit.");
        }

        review.setGuestName(review.getGuestName().trim());
        review.setComment(review.getComment().trim());
        review.setFoodHighlight(review.getFoodHighlight() != null ? review.getFoodHighlight().trim() : "Turkish Grill & Meze");
        review.setCreatedAt(Instant.now());
        return ResponseEntity.ok(reviewRepository.save(review));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteReview(
            @RequestHeader(value = "X-Staff-Token", required = false) String token,
            @PathVariable Long id) {

        if (!StaffController.isAdminToken(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden: Only ADMIN can remove spam/abusive reviews.");
        }
        if (!reviewRepository.existsById(id)) return ResponseEntity.notFound().build();
        reviewRepository.deleteById(id);
        return ResponseEntity.ok("Review deleted.");
    }
}