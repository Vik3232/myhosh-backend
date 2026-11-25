package com.myhosh.booking;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/google-stats")
@CrossOrigin(origins = {
        "https://myhosh-salisbury.co.uk",
        "https://www.myhosh-salisbury.co.uk",
        "http://localhost:5173"
})
public class GoogleStatsController {

    private static final String PLACE_ID = "ChIJid9eqcjrc0gRejtjRW2CRaE";
    private static final Duration CACHE_TTL = Duration.ofHours(12);

    private static final double FALLBACK_RATING = 5.0;
    private static final int FALLBACK_COUNT = 293;

    private static final Pattern RATING_PATTERN =
            Pattern.compile("\"rating\"\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern COUNT_PATTERN =
            Pattern.compile("\"userRatingCount\"\\s*:\\s*([0-9]+)");

    @Value("${GOOGLE_PLACES_API_KEY:}")
    private String apiKey;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();

    private volatile CachedStats cached =
            new CachedStats(FALLBACK_RATING, FALLBACK_COUNT, Instant.EPOCH, false);

    @GetMapping
    public StatsResponse getGoogleStats() {
        CachedStats current = cached;
        if (isExpired(current)) {
            current = refreshStatsSafely();
        }
        return new StatsResponse(current.rating, current.reviewCount, current.live);
    }

    private boolean isExpired(CachedStats stats) {
        return Instant.now().isAfter(stats.fetchedAt.plus(CACHE_TTL));
    }

    private synchronized CachedStats refreshStatsSafely() {
        if (!isExpired(cached)) {
            return cached;
        }

        if (apiKey == null || apiKey.isBlank()) {
            cached = new CachedStats(cached.rating, cached.reviewCount, Instant.now(), false);
            return cached;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://places.googleapis.com/v1/places/" + PLACE_ID))
                    .timeout(Duration.ofSeconds(5))
                    .header("X-Goog-Api-Key", apiKey.trim())
                    .header("X-Goog-FieldMask", "rating,userRatingCount")
                    .GET()
                    .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String body = response.body();
                double rating = extractDouble(RATING_PATTERN, body, cached.rating);
                int count = extractInt(COUNT_PATTERN, body, cached.reviewCount);
                cached = new CachedStats(rating, count, Instant.now(), true);
                return cached;
            }
        } catch (Exception ignored) {
            // Keep serving last known good stats if Google times out
        }

        cached = new CachedStats(
                cached.rating,
                cached.reviewCount,
                Instant.now().minus(CACHE_TTL).plus(Duration.ofMinutes(30)),
                cached.live
        );
        return cached;
    }

    private double extractDouble(Pattern pattern, String text, double fallback) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? Double.parseDouble(matcher.group(1)) : fallback;
    }

    private int extractInt(Pattern pattern, String text, int fallback) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : fallback;
    }

    public static class StatsResponse {
        private final double rating;
        private final int reviewCount;
        private final boolean live;

        public StatsResponse(double rating, int reviewCount, boolean live) {
            this.rating = rating;
            this.reviewCount = reviewCount;
            this.live = live;
        }

        public double getRating() {
            return rating;
        }

        public int getReviewCount() {
            return reviewCount;
        }

        public boolean isLive() {
            return live;
        }
    }

    private static class CachedStats {
        final double rating;
        final int reviewCount;
        final Instant fetchedAt;
        final boolean live;

        CachedStats(double rating, int reviewCount, Instant fetchedAt, boolean live) {
            this.rating = rating;
            this.reviewCount = reviewCount;
            this.fetchedAt = fetchedAt;
            this.live = live;
        }
    }
}