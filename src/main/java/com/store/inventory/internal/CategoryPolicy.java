package com.store.inventory.internal;

import com.store.inventory.api.ProductCategory;
import java.time.Duration;
import java.util.OptionalInt;

public enum CategoryPolicy {

    STANDARD(Duration.ofMinutes(15), OptionalInt.empty()),
    PRE_ORDER(Duration.ofHours(24), OptionalInt.empty()),
    FLASH_SALE(Duration.ofMinutes(5), OptionalInt.of(2));

    private final Duration reservationTtl;
    private final OptionalInt orderLimit;

    CategoryPolicy(Duration reservationTtl, OptionalInt orderLimit) {
        this.reservationTtl = reservationTtl;
        this.orderLimit = orderLimit;
    }

    public Duration reservationTtl() {
        return reservationTtl;
    }

    public OptionalInt orderLimit() {
        return orderLimit;
    }

    public static CategoryPolicy of(ProductCategory category) {
        return switch (category) {
            case STANDARD   -> STANDARD;
            case PRE_ORDER  -> PRE_ORDER;
            case FLASH_SALE -> FLASH_SALE;
        };
    }
}