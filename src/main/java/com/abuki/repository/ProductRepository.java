package com.abuki.repository;

import com.abuki.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Product repository — basic CRUD finders plus a few aggregate
 * queries used by the dashboard (stock status counts).
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    // ── Basic finders ─────────────────────────────────────────────────────────

    /** Find product by its unique SKU code */
    Optional<Product> findBySku(String sku);

    /** All products with a specific status string */
    List<Product> findByStatus(String status);

    /** All products in a specific category */
    List<Product> findByCategory(String category);

    /**
     * Full-text search across name, SKU, and category (case-insensitive).
     * Uses JPQL LOWER + CONCAT — works on both PostgreSQL and H2.
     */
    @Query("SELECT p FROM Product p WHERE " +
           "LOWER(p.name)     LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(p.sku)      LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(p.category) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Product> search(@Param("keyword") String keyword);

    /** Products with stock <= minStock but greater than zero (low stock warning) */
    @Query("SELECT p FROM Product p WHERE p.stock <= p.minStock AND p.stock > 0")
    List<Product> findLowStock();

    /** Products completely out of stock */
    @Query("SELECT p FROM Product p WHERE p.stock = 0")
    List<Product> findOutOfStock();

    // ── Dashboard counts ─────────────────────────────────────────────────────

    /**
     * Count products by their status string.
     * Called with "IN_STOCK", "LOW_STOCK", or "OUT_OF_STOCK".
     * Returns primitive long — safe to assign directly without unboxing.
     */
    @Query("SELECT COUNT(p) FROM Product p WHERE p.status = :status")
    long countByStatus(@Param("status") String status);
}
