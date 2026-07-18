package com.ousman.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;

@Entity
@Table(name = "sales", indexes = {
    @Index(name = "idx_sales_sale_date", columnList = "sale_date"),
    @Index(name = "idx_sales_remaining_loan", columnList = "remaining_loan"),
    @Index(name = "idx_sales_product_id", columnList = "product_id")
})
public class Sale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private Integer quantity;

    // Selling price per unit at time of sale
    @Column(nullable = false)
    private Double price;

    // Total revenue = price * quantity
    @Column(nullable = false)
    private Double total;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "recorded_by")
    private String recordedBy;

    @Column(name = "sale_date", nullable = false)
    private LocalDate saleDate;

    @Column(name = "sale_time")
    private LocalTime saleTime;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // ── Payment tracking fields ───────────────────────────
    // PAID_FULL = fully paid, PARTIAL_LOAN = customer owes some amount
    @Column(name = "payment_status", nullable = false, columnDefinition = "VARCHAR(255) DEFAULT 'PAID_FULL'")
    private String paymentStatus = "PAID_FULL";

    // How much the customer has paid so far
    @Column(name = "paid_amount", nullable = false, columnDefinition = "FLOAT DEFAULT 0.0")
    private Double paidAmount = 0.0;

    // Remaining unpaid balance (total - paidAmount)
    @Column(name = "remaining_loan", nullable = false, columnDefinition = "FLOAT DEFAULT 0.0")
    private Double remainingLoan = 0.0;

    @PrePersist
    protected void onCreate() {
        if (saleDate == null) saleDate = LocalDate.now();
        if (saleTime == null) saleTime = LocalTime.now();
        if (total == null && price != null && quantity != null) {
            total = price * quantity;
        }
        createdAt = LocalDateTime.now();
        // Default payment status if not set
        if (paymentStatus == null) paymentStatus = "PAID_FULL";
        if (paidAmount == null) paidAmount = 0.0;
        if (remainingLoan == null) remainingLoan = 0.0;
    }

    // ── Getters & Setters ────────────────────────────────
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }

    public Double getTotal() { return total; }
    public void setTotal(Double total) { this.total = total; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String recordedBy) { this.recordedBy = recordedBy; }

    public LocalDate getSaleDate() { return saleDate; }
    public void setSaleDate(LocalDate saleDate) { this.saleDate = saleDate; }

    public LocalTime getSaleTime() { return saleTime; }
    public void setSaleTime(LocalTime saleTime) { this.saleTime = saleTime; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    public Double getPaidAmount() { return paidAmount; }
    public void setPaidAmount(Double paidAmount) { this.paidAmount = paidAmount; }

    public Double getRemainingLoan() { return remainingLoan; }
    public void setRemainingLoan(Double remainingLoan) { this.remainingLoan = remainingLoan; }
}
