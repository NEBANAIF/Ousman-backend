package com.ousman.controller;

import com.ousman.dto.AnalyticsDashboardResponse;
import com.ousman.service.AnalyticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/**
 * ─────────────────────────────────────────────────────────────────────────
 *  Analytics Controller — ADMIN only (matches Sidebar's adminOnly: true
 *  flag for the Analytics nav item).
 *
 *  GET /api/analytics/dashboard?from=YYYY-MM-DD&to=YYYY-MM-DD
 *      &granularity=hour|day|month&includeSeries=true|false
 * ─────────────────────────────────────────────────────────────────────────
 */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    @Autowired
    private AnalyticsService analyticsService;

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getDashboard(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(defaultValue = "day") String granularity,
            @RequestParam(defaultValue = "true") boolean includeSeries) {
        try {
            LocalDate fromDate = LocalDate.parse(from);
            LocalDate toDate = LocalDate.parse(to);
            AnalyticsDashboardResponse response = analyticsService.getDashboard(fromDate, toDate, granularity, includeSeries);
            return ResponseEntity.ok(response);
        } catch (java.time.format.DateTimeParseException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "from/to must be dates in YYYY-MM-DD format"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
