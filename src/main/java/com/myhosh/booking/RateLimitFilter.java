package com.myhosh.booking;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimitFilter implements Filter {

    private static class RateWindow {
        volatile long windowStart;
        final AtomicInteger count = new AtomicInteger(0);

        RateWindow(long windowStart) {
            this.windowStart = windowStart;
        }
    }

    private final Map<String, RateWindow> ipBuckets = new ConcurrentHashMap<>();
    private static final long WINDOW_MS = 60_000L; // 60-second window
    private static final int MAX_TRACKED_KEYS = 2_000;

    private String getClientIp(HttpServletRequest request) {
        String cfIp = request.getHeader("CF-Connecting-IP");
        if (cfIp != null && !cfIp.isBlank()) return cfIp.trim();

        String trueClientIp = request.getHeader("True-Client-IP");
        if (trueClientIp != null && !trueClientIp.isBlank()) return trueClientIp.trim();

        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] parts = forwarded.split(",");
            return parts[parts.length - 1].trim();
        }
        return request.getRemoteAddr();
    }

    private boolean isAllowedOrigin(String origin) {
        if (origin == null || origin.isBlank()) return false;
        return origin.matches("^http://localhost:\\d+$")
                || origin.matches("^https://myhosh-[a-zA-Z0-9-]+\\.vercel\\.app$")
                || origin.equals("https://myhosh-salisbury.co.uk")
                || origin.matches("^https://[a-zA-Z0-9-]+\\.myhosh-salisbury\\.co\\.uk$")
                || origin.equals("https://myhosh.co.uk")
                || origin.matches("^https://[a-zA-Z0-9-]+\\.myhosh\\.co\\.uk$");
    }

    private void writeRateLimitResponse(HttpServletRequest request, HttpServletResponse response, String message)
            throws IOException {
        // Attach CORS headers on short-circuited 429s since Spring MVC CorsConfig won't execute
        String origin = request.getHeader("Origin");
        if (isAllowedOrigin(origin)) {
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.setHeader("Vary", "Origin");
            response.setHeader("Access-Control-Expose-Headers", "Retry-After");
        }
        response.setStatus(429);
        response.setHeader("Retry-After", "60");
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write(message);
    }

    private void evictExpiredBucketsIfNeeded(long now) {
        if (ipBuckets.size() > MAX_TRACKED_KEYS) {
            ipBuckets.entrySet().removeIf(entry -> now - entry.getValue().windowStart > WINDOW_MS * 2);
        }
    }

    private boolean isAllowed(String key, int maxRequests) {
        long now = System.currentTimeMillis();
        evictExpiredBucketsIfNeeded(now);

        RateWindow bucket = ipBuckets.computeIfAbsent(key, k -> new RateWindow(now));
        synchronized (bucket) {
            if (now - bucket.windowStart > WINDOW_MS) {
                bucket.windowStart = now;
                bucket.count.set(1);
                return true;
            }
            return bucket.count.incrementAndGet() <= maxRequests;
        }
    }

    private void applySecurityHeaders(HttpServletResponse response, String path) {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        if (path != null && (path.startsWith("/api/staff") || path.startsWith("/api/customers"))) {
            response.setHeader("Cache-Control", "no-store");
        }
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        String path = request.getRequestURI();
        String method = request.getMethod();

        applySecurityHeaders(response, path);

        if ("OPTIONS".equalsIgnoreCase(method) || "/api/health".equals(path)) {
            chain.doFilter(req, res);
            return;
        }

        String clientIp = getClientIp(request);

        if ("/api/staff/login".equals(path) && "POST".equalsIgnoreCase(method)) {
            if (!isAllowed(clientIp + ":LOGIN", 5)) {
                writeRateLimitResponse(request, response, "Too many login attempts. Please wait 60 seconds and try again.");
                return;
            }
        } else if (("/api/bookings".equals(path) || "/api/reviews".equals(path)) && "POST".equalsIgnoreCase(method)) {
            if (!isAllowed(clientIp + ":PUBLIC_POST", 10)) {
                writeRateLimitResponse(request, response, "Too many requests from this network. Please wait a minute.");
                return;
            }
        } else {
            if (!isAllowed(clientIp + ":GENERAL", 60)) {
                writeRateLimitResponse(request, response, "Rate limit exceeded. Please slow down.");
                return;
            }
        }

        chain.doFilter(req, res);
    }
}