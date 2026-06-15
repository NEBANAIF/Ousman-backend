package com.abuki.controller;

import com.abuki.model.StockHistory;
import com.abuki.service.StockHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stock-history")
public class StockHistoryController {

    @Autowired
    private StockHistoryService stockHistoryService;

    // GET /api/stock-history
    @GetMapping
    public ResponseEntity<List<StockHistory>> getAll() {
        return ResponseEntity.ok(stockHistoryService.getAll());
    }

    // GET /api/stock-history/product/{productId}
    @GetMapping("/product/{productId}")
    public ResponseEntity<List<StockHistory>> getByProduct(@PathVariable Long productId) {
        return ResponseEntity.ok(stockHistoryService.getByProduct(productId));
    }

    // DELETE /api/stock-history/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            stockHistoryService.delete(id);
            return ResponseEntity.ok("Record deleted");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}