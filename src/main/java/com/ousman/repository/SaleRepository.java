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

    /** All sales in a date range (inclusive on both ends, using BETWEEN). */
    List<Sale> findBySaleDateBetween(LocalDate from, LocalDate to);

    /**
     * All sales in a date range — half-open [from, to+1day). Prefer this over
     * findBySaleDateBetween for anything that needs to reliably include every
     * sale ON the "to" day: if sale_date is ever compared with a hidden
     * midnight time component, BETWEEN's upper bound can silently exclude
     * same-day sales recorded after midnight (e.g. a 5 AM sale on the last
     * day of the range). This form can't have that problem.
     */
    @Query("SELECT s FROM Sale s WHERE s.saleDate >= :from AND s.saleDate < :toExclusive")
    List<Sale> findBySaleDateInRange(@Param("from") LocalDate from, @Param("toExclusive") LocalDate toExclusive);

    /** All sales for a specific product */
    List<Sale> findByProductId(Long productId);

    /** All sales ordered newest first */
    @Query("SELECT s FROM Sale s ORDER BY s.saleDate DESC, s.saleTime DESC")
    List<Sale> findAllOrderedByDate();

    // ── Dashboard aggregates ─────────────────────────────────────────────────

    /** Total units sold between from and to inclusive. */
    @Query("SELECT COALESCE(SUM(s.quantity), 0) FROM Sale s WHERE s.saleDate >= :from AND s.saleDate < :toExclusive")
    Long sumQuantityBetween(@Param("from") LocalDate from, @Param("toExclusive") LocalDate toExclusive);

    /** Number of sale transactions between from and to inclusive. */
    @Query("SELECT COUNT(s) FROM Sale s WHERE s.saleDate >= :from AND s.saleDate < :toExclusive")
    Long countSalesBetween(@Param("from") LocalDate from, @Param("toExclusive") LocalDate toExclusive);

    /** All sales with an outstanding loan balance (PARTIAL_LOAN). */
    @Query("SELECT s FROM Sale s WHERE s.remainingLoan > 0 ORDER BY s.saleDate DESC, s.saleTime DESC")
    List<Sale> findOutstandingLoans();

    /** Total outstanding loan balance across all sales. */
    @Query("SELECT COALESCE(SUM(s.remainingLoan), 0) FROM Sale s WHERE s.remainingLoan > 0")
    Double sumOutstandingLoans();
}