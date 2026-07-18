package com.ousman.repository;

import com.ousman.model.Sale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Sale repository — basic finders plus a small set of aggregate
 * queries used by the dashboard (sold items, stock movement, loans).
 */
@Repository
public interface SaleRepository extends JpaRepository<Sale, Long> {

    // ── Basic finders ─────────────────────────────────────────────────────────

    /** All sales on a specific date */
    List<Sale> findBySaleDate(LocalDate date);

    /** All sales in a date range */
    List<Sale> findBySaleDateBetween(LocalDate from, LocalDate to);

    /** All sales for a specific product */
    List<Sale> findByProductId(Long productId);

    /** All sales ordered newest first */
    @Query("SELECT s FROM Sale s ORDER BY s.saleDate DESC, s.saleTime DESC")
    List<Sale> findAllOrderedByDate();

    // ── Dashboard aggregates ─────────────────────────────────────────────────

    /** Total units sold between from and to inclusive. */
    @Query("SELECT COALESCE(SUM(s.quantity), 0) FROM Sale s WHERE s.saleDate BETWEEN :from AND :to")
    Long sumQuantityBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Number of sale transactions between from and to inclusive. */
    @Query("SELECT COUNT(s) FROM Sale s WHERE s.saleDate BETWEEN :from AND :to")
    Long countSalesBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** All sales with an outstanding loan balance (PARTIAL_LOAN). */
    @Query("SELECT s FROM Sale s WHERE s.remainingLoan > 0 ORDER BY s.saleDate DESC, s.saleTime DESC")
    List<Sale> findOutstandingLoans();

    /** Total outstanding loan balance across all sales. */
    @Query("SELECT COALESCE(SUM(s.remainingLoan), 0) FROM Sale s WHERE s.remainingLoan > 0")
    Double sumOutstandingLoans();
}
