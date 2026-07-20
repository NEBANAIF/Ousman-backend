package com.ousman.service;

import com.ousman.dto.AnalyticsDashboardResponse;
import com.ousman.dto.AnalyticsProductStat;
import com.ousman.dto.AnalyticsSeriesPoint;
import com.ousman.model.Sale;
import com.ousman.repository.SaleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the payload for GET /api/analytics/dashboard.
 *
 * NOTE ON PROFIT/EXPENSES: Product/Sale do not currently track a cost price
 * (COGS), and there is no Expense entity/table in the backend at all —
 * Finance.jsx calls GET /api/expenses but no controller serves it (the
 * frontend already catches that failure and falls back to an empty list).
 * Until those exist, "cogs" and "expenses" here are always 0, and
 * "grossProfit"/"netProfit" both equal total revenue. This is a placeholder,
 * not a real margin calculation — add a `costPrice` field to Product and an
 * Expense entity/controller to compute genuine figures.
 */
@Service
public class AnalyticsService {

    @Autowired
    private SaleRepository saleRepository;

    private static final DateTimeFormatter DAY_LABEL   = DateTimeFormatter.ofPattern("MMM d");
    private static final DateTimeFormatter MONTH_LABEL  = DateTimeFormatter.ofPattern("MMM yyyy");
    private static final DateTimeFormatter MONTH_KEY    = DateTimeFormatter.ofPattern("yyyy-MM");

    public AnalyticsDashboardResponse getDashboard(LocalDate from, LocalDate to, String granularity, boolean includeSeries) {
        List<Sale> sales = saleRepository.findBySaleDateInRange(from, to.plusDays(1));

        double totalRevenue = sales.stream().mapToDouble(s -> s.getTotal() != null ? s.getTotal() : 0.0).sum();
        long saleCount = sales.size();
        long totalQuantity = sales.stream().mapToLong(s -> s.getQuantity() != null ? s.getQuantity() : 0).sum();
        double avgOrderValue = saleCount > 0 ? totalRevenue / saleCount : 0.0;

        // No COGS or expense tracking exists yet — see class-level note.
        // These are placeholders (0 / revenue) so the Finance page has real
        // numbers to render instead of missing fields that crash the UI.
        double cogs = 0.0;
        double expenses = 0.0;
        double grossProfit = totalRevenue - cogs;
        double grossMarginPct = totalRevenue > 0 ? (grossProfit / totalRevenue) * 100.0 : 0.0;
        double netProfit = grossProfit - expenses;
        double netMarginPct = totalRevenue > 0 ? (netProfit / totalRevenue) * 100.0 : 0.0;

        AnalyticsDashboardResponse response = new AnalyticsDashboardResponse();
        response.setTotalRevenue(round2(totalRevenue));
        response.setSaleCount(saleCount);
        response.setTotalQuantity(totalQuantity);
        response.setAvgOrderValue(round2(avgOrderValue));
        response.setCogs(round2(cogs));
        response.setExpenses(round2(expenses));
        response.setGrossProfit(round2(grossProfit));
        response.setGrossMarginPct(round2(grossMarginPct));
        response.setNetProfit(round2(netProfit));
        response.setNetMarginPct(round2(netMarginPct));
        response.setTopProducts(buildTopProducts(sales));
        response.setSeries(includeSeries ? buildSeries(sales, granularity, from, to) : new ArrayList<>());
        return response;
    }

    private List<AnalyticsProductStat> buildTopProducts(List<Sale> sales) {
        Map<String, double[]> byProduct = new LinkedHashMap<>(); // name -> [revenue, quantity]
        for (Sale s : sales) {
            if (s.getProduct() == null) continue;
            String name = s.getProduct().getName();
            double revenue = s.getTotal() != null ? s.getTotal() : 0.0;
            int qty = s.getQuantity() != null ? s.getQuantity() : 0;
            byProduct.computeIfAbsent(name, k -> new double[2]);
            double[] agg = byProduct.get(name);
            agg[0] += revenue;
            agg[1] += qty;
        }
        List<AnalyticsProductStat> stats = new ArrayList<>();
        for (Map.Entry<String, double[]> e : byProduct.entrySet()) {
            stats.add(new AnalyticsProductStat(e.getKey(), round2(e.getValue()[0]), (int) e.getValue()[1]));
        }
        stats.sort(Comparator.comparingDouble(AnalyticsProductStat::getRevenue).reversed());
        return stats;
    }

    private List<AnalyticsSeriesPoint> buildSeries(List<Sale> sales, String granularity, LocalDate from, LocalDate to) {
        String g = granularity == null ? "day" : granularity.toLowerCase();
        Map<String, Object[]> buckets = new LinkedHashMap<>(); // dateKey -> [label, revenueSum, qtySum]

        // Pre-fill every bucket across the whole range with zero so the chart
        // always has a continuous line/series — without this, a range where
        // only one day (or hour/month) has sales produces a single isolated
        // point, which a line chart just renders as a dot with nothing to
        // connect it to.
        switch (g) {
            case "hour": {
                // Hour granularity is only meaningful for a single-day range
                // (that's the only case the frontend uses it for), so fill
                // all 24 hours of "from".
                for (int hour = 0; hour < 24; hour++) {
                    String key = from + "T" + String.format("%02d", hour);
                    String label = String.format("%02d:00", hour);
                    buckets.put(key, new Object[]{label, 0.0, 0});
                }
                break;
            }
            case "month": {
                YearMonth start = YearMonth.from(from);
                YearMonth end = YearMonth.from(to);
                for (YearMonth ym = start; !ym.isAfter(end); ym = ym.plusMonths(1)) {
                    String key = ym.format(MONTH_KEY);
                    String label = ym.atDay(1).format(MONTH_LABEL);
                    buckets.put(key, new Object[]{label, 0.0, 0});
                }
                break;
            }
            case "day":
            default: {
                for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                    String key = d.toString();
                    String label = d.format(DAY_LABEL);
                    buckets.put(key, new Object[]{label, 0.0, 0});
                }
                break;
            }
        }

        for (Sale s : sales) {
            LocalDate date = s.getSaleDate();
            if (date == null) continue;
            String key;
            String label;

            switch (g) {
                case "hour": {
                    LocalTime time = s.getSaleTime() != null ? s.getSaleTime() : LocalTime.MIDNIGHT;
                    int hour = time.getHour();
                    key = date + "T" + String.format("%02d", hour);
                    label = String.format("%02d:00", hour);
                    break;
                }
                case "month": {
                    YearMonth ym = YearMonth.from(date);
                    key = ym.format(MONTH_KEY);
                    label = ym.atDay(1).format(MONTH_LABEL);
                    break;
                }
                case "day":
                default: {
                    key = date.toString();
                    label = date.format(DAY_LABEL);
                    break;
                }
            }

            buckets.computeIfAbsent(key, k -> new Object[]{label, 0.0, 0});
            Object[] agg = buckets.get(key);
            double revenue = s.getTotal() != null ? s.getTotal() : 0.0;
            int qty = s.getQuantity() != null ? s.getQuantity() : 0;
            agg[1] = ((double) agg[1]) + revenue;
            agg[2] = ((int) agg[2]) + qty;
        }

        List<AnalyticsSeriesPoint> points = new ArrayList<>();
        for (Map.Entry<String, Object[]> e : buckets.entrySet()) {
            String key = e.getKey();
            Object[] agg = e.getValue();
            String label = (String) agg[0];
            double revenue = round2((double) agg[1]);
            int qty = (int) agg[2];
            // No COGS tracked yet: profit mirrors revenue, loss is always 0 (see class note).
            points.add(new AnalyticsSeriesPoint(label, key, revenue, qty, revenue, 0.0));
        }
        points.sort(Comparator.comparing(AnalyticsSeriesPoint::getDateKey));
        return points;
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}