package com.store.inventory.internal;

import java.time.Instant;

public final class ActiveReservation {

    private final String orderId;
    private final String sku;
    private final int quantity;
    private final Instant expiresAt;

    public ActiveReservation(String orderId, String sku, int quantity, Instant expiresAt) {
        this.orderId   = orderId;
        this.sku       = sku;
        this.quantity  = quantity;
        this.expiresAt = expiresAt;
    }

    public String orderId()    { return orderId; }
    public String sku()        { return sku; }
    public int quantity()      { return quantity; }
    public Instant expiresAt() { return expiresAt; }

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }
}