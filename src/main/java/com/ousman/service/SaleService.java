package com.ousman.service;

import com.ousman.model.Product;
import com.ousman.model.Sale;
import com.ousman.repository.SaleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Sale service — read operations cached, write operations evict
 * the "products" cache.
 */
@Service
@Transactional
public class SaleService {

    @Autowired
    private SaleRepository saleRepository;

    @Autowired
    private ProductService productService;

    // ── Read: cached ──────────────────────────────────────────────────────

    @Cacheable(value = "products", key = "'allSales'")
    @Transactional(readOnly = true)
    public List<Sale> getAll() {
        return saleRepository.findAllOrderedByDate();
    }

    // Queries only the requested date's rows (indexed column) instead of
    // loading every historical sale — used by /api/sales/today so that
    // endpoint stays fast as the sales table grows.
    @Cacheable(value = "products", key = "'salesByDate:' + #date")
    @Transactional(readOnly = true)
    public List<Sale> getByDate(LocalDate date) {
        return saleRepository.findBySaleDate(date);
    }

    @Cacheable(value = "products", key = "'sale:' + #id")
    @Transactional(readOnly = true)
    public Sale getById(Long id) {
        return saleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sale not found with id: " + id));
    }

    // ── Write: cache-evicting ─────────────────────────────────────────────

    @CacheEvict(value = "products", allEntries = true)
    public Sale recordSale(Sale sale) {
        Product product = productService.getById(sale.getProduct().getId());

        // ── Required: customer name ───────────────────────────────────────
        if (sale.getCustomerName() == null || sale.getCustomerName().isBlank()) {
            throw new RuntimeException("Customer name is required.");
        }

        // Validate sufficient stock before recording the sale
        if (product.getStock() < sale.getQuantity()) {
            throw new RuntimeException(
                "Insufficient stock for '" + product.getName() +
                "'. Available: " + product.getStock() +
                ", Requested: " + sale.getQuantity());
        }

        // Use product price if sale price not supplied by frontend
        if (sale.getPrice() == null) {
            sale.setPrice(product.getPrice());
        }

        // Calculate and set total revenue for this sale
        double total = sale.getPrice() * sale.getQuantity();
        sale.setTotal(total);
        sale.setProduct(product);

        // ── Payment validation ────────────────────────────────────────────
        String status = sale.getPaymentStatus();
        if (status == null || status.isBlank()) status = "PAID_FULL";
        sale.setPaymentStatus(status);

        if ("PAID_FULL".equals(status)) {
            sale.setPaidAmount(total);
            sale.setRemainingLoan(0.0);
        } else if ("PARTIAL_LOAN".equals(status)) {
            double paid = sale.getPaidAmount() != null ? sale.getPaidAmount() : 0.0;
            if (paid < 0)     throw new RuntimeException("Paid amount cannot be negative.");
            if (paid > total) throw new RuntimeException("Paid amount (" + paid + ") cannot exceed the total (" + total + ").");
            sale.setPaidAmount(paid);
            sale.setRemainingLoan(total - paid);
        } else {
            throw new RuntimeException("Invalid payment status: " + status);
        }

        // Persist the sale row
        Sale saved = saleRepository.save(sale);

        // Deduct stock and record the change in stock history
        int prevStock = product.getStock();
        int newStock  = prevStock - sale.getQuantity();
        product.setStock(newStock);

        productService.recordHistory(
            product,
            -sale.getQuantity(),
            prevStock, newStock,
            "SALE",
            "Sale to " + sale.getCustomerName(),
            sale.getRecordedBy() != null ? sale.getRecordedBy() : "Admin",
            "SALE-" + saved.getId()
        );

        productService.update(product.getId(), product);

        return saved;
    }

    /**
     * Partial update — only touches payment fields.
     * Called from the Loan page to record a repayment.
     * If paidAmount reaches total, paymentStatus flips to PAID_FULL.
     */
    @CacheEvict(value = "products", allEntries = true)
    public Sale updateSalePayment(Long id, Double newPaidAmount) {
        Sale sale = saleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sale not found with id: " + id));

        double total = sale.getTotal() != null ? sale.getTotal() : 0.0;

        if (newPaidAmount < 0)
            throw new RuntimeException("Paid amount cannot be negative.");
        if (newPaidAmount > total)
            throw new RuntimeException("Paid amount (" + newPaidAmount + ") cannot exceed the total (" + total + ").");

        double remaining = total - newPaidAmount;
        sale.setPaidAmount(newPaidAmount);
        sale.setRemainingLoan(remaining);
        sale.setPaymentStatus(remaining == 0.0 ? "PAID_FULL" : "PARTIAL_LOAN");

        return saleRepository.save(sale);
    }

    @CacheEvict(value = "products", allEntries = true)
    public void deleteSale(Long id) {
        Sale sale    = saleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sale not found with id: " + id));

        // Loan records (partially paid or otherwise carrying a balance) can never be
        // deleted, by any role — this is a hard business rule, not just a UI restriction.
        if ("PARTIAL_LOAN".equals(sale.getPaymentStatus()) || sale.getRemainingLoan() > 0.0) {
            throw new RuntimeException("Loan records cannot be deleted.");
        }

        Product product = sale.getProduct();

        int prevStock = product.getStock();
        int newStock  = prevStock + sale.getQuantity();
        product.setStock(newStock);

        productService.recordHistory(
            product,
            sale.getQuantity(),
            prevStock, newStock,
            "ADJUSTMENT",
            "Stock restored — sale #" + id + " deleted",
            "System",
            "SALE-DEL-" + id
        );

        productService.update(product.getId(), product);
        saleRepository.delete(sale);
    }
}
