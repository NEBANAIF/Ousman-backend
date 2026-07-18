package com.ousman.controller;

import com.ousman.model.Sale;
import com.ousman.service.SaleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * ─────────────────────────────────────────────────────────────────────────
 *  Sale Controller — Role-Based Access
 *
 *  ADMIN:  GET all, GET by id, POST (record), PUT payment, DELETE
 *  WORKER: GET all, GET /today, POST (record sale), PUT payment
 *          DELETE → 403 (blocked in SecurityConfig + @PreAuthorize)
 *
 *  Workers get full read access to sales/loan history (needed for the
 *  Loans page, which must total outstanding debt across ALL sales, not
 *  just today's). The /today endpoint remains available for quick
 *  "today only" views on the Sales dashboard.
 * ─────────────────────────────────────────────────────────────────────────
 */
@RestController
@RequestMapping("/api/sales")
public class SaleController {

    @Autowired
    private SaleService saleService;

    // ── GET /api/sales — ADMIN + WORKER (full sales/loan history) ─────────
    // Workers need this for both the Sales page and the Loans page, since
    // outstanding loans must be calculated across all historical sales,
    // not just today's.
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'WORKER')")
    public ResponseEntity<List<Sale>> getAll() {
        return ResponseEntity.ok(saleService.getAll());
    }

    // ── GET /api/sales/today — WORKER + ADMIN (only today's sales) ────────
    // Workers call this endpoint; admins can also use it for a quick today view
    @GetMapping("/today")
    @PreAuthorize("hasAnyRole('ADMIN', 'WORKER')")
    public ResponseEntity<List<Sale>> getToday() {
        // Query only today's rows at the database level (indexed on sale_date)
        // instead of loading the entire sales table into memory and filtering
        // in Java — this used to load and deserialize every historical sale
        // just to throw most of it away, which gets slower every day as the
        // table grows.
        return ResponseEntity.ok(saleService.getByDate(LocalDate.now()));
    }

    // ── GET /api/sales/{id} — ADMIN only ──────────────────────────────────
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Sale> getById(@PathVariable Long id) {
        return ResponseEntity.ok(saleService.getById(id));
    }

    // ── POST /api/sales — ADMIN + WORKER (record a new sale) ──────────────
    // Both roles can record sales
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'WORKER')")
    public ResponseEntity<?> recordSale(
            @RequestBody Sale sale,
            @AuthenticationPrincipal String email) {
        try {
            // Auto-tag who recorded the sale based on the JWT principal (email)
            if (sale.getRecordedBy() == null || sale.getRecordedBy().isBlank()) {
                sale.setRecordedBy(email);
            }
            return ResponseEntity.ok(saleService.recordSale(sale));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── PUT /api/sales/{id}/payment — ADMIN + WORKER (record a loan payment) ──
    @PutMapping("/{id}/payment")
    @PreAuthorize("hasAnyRole('ADMIN', 'WORKER')")
    public ResponseEntity<?> updatePayment(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        try {
            Double newPaidAmount = body.get("paidAmount") instanceof Number n
                    ? n.doubleValue()
                    : Double.parseDouble(body.get("paidAmount").toString());
            return ResponseEntity.ok(saleService.updateSalePayment(id, newPaidAmount));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── DELETE /api/sales/{id} — ADMIN only ───────────────────────────────
    // SecurityConfig already blocks DELETE for WORKER with 403
    // This @PreAuthorize is an extra layer of protection
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteSale(@PathVariable Long id) {
        try {
            saleService.deleteSale(id);
            return ResponseEntity.ok(Map.of("message", "Sale deleted and stock restored"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}