package com.store.inventory.internal;

import com.store.inventory.api.ProductCategory;

public final class ProductEntry {

    private final ProductCategory category;
    private int totalStock;
    private int soldUnits;
    private boolean alertSent;

    public ProductEntry(ProductCategory category) {
        this.category   = category;
        this.totalStock = 0;
        this.soldUnits  = 0;
        this.alertSent  = false;
    }

    public ProductCategory category() { return category; }
    public int totalStock()           { return totalStock; }
    public int soldUnits()            { return soldUnits; }
    public boolean alertSent()        { return alertSent; }

    public void addStock(int quantity) {
        totalStock += quantity;
        alertSent = false;
    }

    public void sell(int quantity) {
        soldUnits += quantity;
    }

    public void markAlertSent() {
        alertSent = true;
    }
}