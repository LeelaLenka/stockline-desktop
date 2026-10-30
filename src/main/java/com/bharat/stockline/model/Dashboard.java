package com.bharat.stockline.model;

import java.util.List;

/** Everything the dashboard shows, computed with SQL aggregates */
public record Dashboard(long revenueTodayPaise, int ordersToday, long unitsInStock, int lowStock, int outOfStock,
                        int productCount, int storeCount, List<DaySales> week, List<TopProduct> topProducts,
                        List<StockLine> restockSoon) {

    public record DaySales(String label, long totalPaise) {}

    public record TopProduct(String name, int units, long revenuePaise) {}
}
