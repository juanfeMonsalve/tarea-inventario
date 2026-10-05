package com.store.inventory;

import com.store.inventory.api.InsufficientStockException;
import com.store.inventory.api.InventoryService;
import com.store.inventory.api.OrderLimitExceededException;
import com.store.inventory.api.ProductCategory;
import com.store.inventory.api.Reservation;
import com.store.inventory.api.StockAlertListener;
import com.store.inventory.internal.ActiveReservation;
import com.store.inventory.internal.CategoryPolicy;
import com.store.inventory.internal.ProductEntry;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

final class InventoryServiceImpl implements InventoryService {

    private static final int LOW_STOCK_THRESHOLD = 5;

    private final Clock clock;
    private final StockAlertListener alertListener;
    private final Map<String, ProductEntry> products = new HashMap<>();
    private final Map<String, ActiveReservation> reservations = new HashMap<>();

    InventoryServiceImpl(Clock clock, StockAlertListener alertListener) {
        this.clock         = clock;
        this.alertListener = alertListener;
    }

    @Override
    public synchronized void registerProduct(String sku, ProductCategory category) {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("SKU must not be blank");
        }
        products.putIfAbsent(sku, new ProductEntry(category));
    }

    @Override
    public synchronized void addStock(String sku, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive, got: " + quantity);
        }
        requireProduct(sku).addStock(quantity);
    }

    @Override
    public synchronized Reservation reserve(String orderId, String sku, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive, got: " + quantity);
        }
        ProductEntry entry    = requireProduct(sku);
        CategoryPolicy policy = CategoryPolicy.of(entry.category());
        policy.orderLimit().ifPresent(limit -> {
            if (quantity > limit) {
                throw new OrderLimitExceededException(sku, quantity, limit);
            }
        });
        purgeExpired();
        int avail = computeAvailable(sku);
        if (quantity > avail) {
            throw new InsufficientStockException(sku, quantity, avail);
        }
        Instant expiresAt = clock.instant().plus(policy.reservationTtl());
        reservations.put(orderId, new ActiveReservation(orderId, sku, quantity, expiresAt));
        maybeFireAlert(sku, entry);
        return new Reservation(orderId, sku, quantity, expiresAt);
    }

    @Override
    public synchronized void confirm(String orderId) {
        purgeExpired();
        ActiveReservation active = reservations.remove(orderId);
        if (active == null) {
            throw new IllegalStateException("No active reservation for order: " + orderId);
        }
        ProductEntry entry = products.get(active.sku());
        if (entry != null) {
            entry.sell(active.quantity());
        }
    }

    @Override
    public synchronized int available(String sku) {
        purgeExpired();
        return computeAvailable(sku);
    }

    private int computeAvailable(String sku) {
        ProductEntry entry = products.get(sku);
        if (entry == null) {
            return 0;
        }
        int reserved = reservations.values().stream()
                .filter(r -> r.sku().equals(sku))
                .mapToInt(ActiveReservation::quantity)
                .sum();
        return entry.totalStock() - entry.soldUnits() - reserved;
    }

    private void purgeExpired() {
        Instant now = clock.instant();
        Iterator<ActiveReservation> it = reservations.values().iterator();
        while (it.hasNext()) {
            if (it.next().isExpired(now)) {
                it.remove();
            }
        }
    }

    private void maybeFireAlert(String sku, ProductEntry entry) {
        if (entry.alertSent()) {
            return;
        }
        int avail = computeAvailable(sku);
        if (avail <= LOW_STOCK_THRESHOLD) {
            entry.markAlertSent();
            alertListener.onLowStock(sku, avail);
        }
    }

    private ProductEntry requireProduct(String sku) {
        ProductEntry entry = products.get(sku);
        if (entry == null) {
            throw new IllegalArgumentException("Product not registered: " + sku);
        }
        return entry;
    }
}