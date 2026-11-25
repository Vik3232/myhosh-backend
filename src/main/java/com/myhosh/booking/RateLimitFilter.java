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
        // Render / Cloudflare edge overwrites these headers with the true visitor IP
        String cfIp = request.getHeader("CF-Connecting-IP");
        if (cfIp != null && !cfIp.isBlank()) {
            return cfIp.trim();
        }
        String trueClientIp = request.getHeader("True-Client-IP");
        if (trueClientIp != null && !trueClientIp.isBlank()) {
            return trueClientIp.trim();
        }
        // Never trust index [0] of X-Forwarded-For (attacker-spoofable); use the rightmost proxy-appended IP
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] parts = forwarded.split(",");
            return parts[parts.length - 1].trim();
        }
        return request.getRemoteAddr();
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

        // Always allow CORS preflight OPTIONS and Uptime Robot health checks
        if ("OPTIONS".equalsIgnoreCase(method) || "/api/health".equals(path)) {
            chain.doFilter(req, res);
            return;
        }

        String clientIp = getClientIp(request);

        // 1. Strict Brute-Force Shield on Staff Login (Max 5 per minute per IP)
        if ("/api/staff/login".equals(path) && "POST".equalsIgnoreCase(method)) {
            if (!isAllowed(clientIp + ":LOGIN", 5)) {
                response.setStatus(429);
                response.setHeader("Retry-After", "60");
                response.setContentType("text/plain;charset=UTF-8");
                response.getWriter().write("Too many login attempts. Please wait 60 seconds and try again.");
                return;
            }
        }
        // 2. Anti-Bot Shield on Reservation & Review Creation (Max 10 per minute per IP)
        else if (("/api/bookings".equals(path) || "/api/reviews".equals(path)) && "POST".equalsIgnoreCase(method)) {
            if (!isAllowed(clientIp + ":PUBLIC_POST", 10)) {
                response.setStatus(429);
                response.setHeader("Retry-After", "60");
                response.setContentType("text/plain;charset=UTF-8");
                response.getWriter().write("Too many requests from this network. Please wait a minute.");
                return;
            }
        }
        // 3. General API Flood Protection (Max 60 requests per minute per IP)
        else {
            if (!isAllowed(clientIp + ":GENERAL", 60)) {
                response.setStatus(429);
                response.setHeader("Retry-After", "60");
                response.setContentType("text/plain;charset=UTF-8");
                response.getWriter().write("Rate limit exceeded. Please slow down.");
                return;
            }
        }

        chain.doFilter(req, res);
    }
}