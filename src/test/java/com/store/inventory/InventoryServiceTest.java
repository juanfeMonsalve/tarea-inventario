package com.store.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.store.inventory.api.InsufficientStockException;
import com.store.inventory.api.InventoryService;
import com.store.inventory.api.OrderLimitExceededException;
import com.store.inventory.api.ProductCategory;
import com.store.inventory.api.Reservation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

class InventoryServiceTest {

    private InventoryService service;

    @BeforeEach
    void setUp() {
        service = Inventory.create(Clock.systemUTC(), (sku, available) -> { });
        service.registerProduct("SKU-1", ProductCategory.STANDARD);
    }

    @Test
    void reservingReducesAvailableUnits() {
        service.addStock("SKU-1", 10);
        service.reserve("ORDER-1", "SKU-1", 3);
        assertEquals(7, service.available("SKU-1"));
    }

    @Test
    void cannotReserveMoreThanAvailable() {
        service.addStock("SKU-1", 2);
        assertThrows(InsufficientStockException.class, () -> service.reserve("ORDER-1", "SKU-1", 3));
    }

    @Test
    void confirmedUnitsStaySold() {
        service.addStock("SKU-1", 5);
        service.reserve("ORDER-1", "SKU-1", 2);
        service.confirm("ORDER-1");
        assertEquals(3, service.available("SKU-1"));
    }

    @Nested
    class ReservationExpiry {

        @Test
        void expiredReservationReleasesUnitsBackToStock() {
            MutableClock clock = new MutableClock(Instant.parse("2024-01-01T00:00:00Z"));
            InventoryService svc = Inventory.create(clock, (sku, n) -> { });
            svc.registerProduct("SKU-A", ProductCategory.STANDARD);
            svc.addStock("SKU-A", 5);
            svc.reserve("ORDER-1", "SKU-A", 5);
            assertEquals(0, svc.available("SKU-A"));
            clock.advance(Duration.ofMinutes(16));
            assertEquals(5, svc.available("SKU-A"));
        }

        @Test
        void reservationNotExpiredBeforeDeadline() {
            MutableClock clock = new MutableClock(Instant.parse("2024-01-01T00:00:00Z"));
            InventoryService svc = Inventory.create(clock, (sku, n) -> { });
            svc.registerProduct("SKU-A", ProductCategory.STANDARD);
            svc.addStock("SKU-A", 5);
            svc.reserve("ORDER-1", "SKU-A", 5);
            clock.advance(Duration.ofMinutes(14));
            assertEquals(0, svc.available("SKU-A"));
        }

        @Test
        void flashSaleExpiresAfterFiveMinutes() {
            MutableClock clock = new MutableClock(Instant.parse("2024-01-01T00:00:00Z"));
            InventoryService svc = Inventory.create(clock, (sku, n) -> { });
            svc.registerProduct("FLASH-1", ProductCategory.FLASH_SALE);
            svc.addStock("FLASH-1", 2);
            svc.reserve("ORDER-1", "FLASH-1", 2);
            clock.advance(Duration.ofMinutes(6));
            assertEquals(2, svc.available("FLASH-1"));
        }

        @Test
        void preOrderExpiresAfter24Hours() {
            MutableClock clock = new MutableClock(Instant.parse("2024-01-01T00:00:00Z"));
            InventoryService svc = Inventory.create(clock, (sku, n) -> { });
            svc.registerProduct("PRE-1", ProductCategory.PRE_ORDER);
            svc.addStock("PRE-1", 3);
            svc.reserve("ORDER-1", "PRE-1", 3);
            clock.advance(Duration.ofHours(25));
            assertEquals(3, svc.available("PRE-1"));
        }

        @Test
        void expiredReservationCanBeReservedByAnotherOrder() {
            MutableClock clock = new MutableClock(Instant.parse("2024-01-01T00:00:00Z"));
            InventoryService svc = Inventory.create(clock, (sku, n) -> { });
            svc.registerProduct("SKU-A", ProductCategory.STANDARD);
            svc.addStock("SKU-A", 3);
            svc.reserve("ORDER-1", "SKU-A", 3);
            clock.advance(Duration.ofMinutes(16));
            svc.reserve("ORDER-2", "SKU-A", 3);
            assertEquals(0, svc.available("SKU-A"));
        }

        @Test
        void confirmAfterExpiryThrows() {
            MutableClock clock = new MutableClock(Instant.parse("2024-01-01T00:00:00Z"));
            InventoryService svc = Inventory.create(clock, (sku, n) -> { });
            svc.registerProduct("SKU-A", ProductCategory.STANDARD);
            svc.addStock("SKU-A", 5);
            svc.reserve("ORDER-1", "SKU-A", 2);
            clock.advance(Duration.ofMinutes(16));
            assertThrows(IllegalStateException.class, () -> svc.confirm("ORDER-1"));
        }

        @Test
        void reservationReturnsCorrectExpiresAt() {
            MutableClock clock = new MutableClock(Instant.parse("2024-01-01T00:00:00Z"));
            InventoryService svc = Inventory.create(clock, (sku, n) -> { });
            svc.registerProduct("SKU-A", ProductCategory.STANDARD);
            svc.addStock("SKU-A", 5);
            Reservation r = svc.reserve("ORDER-1", "SKU-A", 1);
            assertEquals(Instant.parse("2024-01-01T00:15:00Z"), r.expiresAt());
        }
    }

    @Nested
    class CategoryRules {

        @Test
        void flashSaleRejectsMoreThanTwoUnitsPerOrder() {
            service.registerProduct("FLASH-1", ProductCategory.FLASH_SALE);
            service.addStock("FLASH-1", 10);
            assertThrows(OrderLimitExceededException.class,
                    () -> service.reserve("ORDER-1", "FLASH-1", 3));
        }

        @Test
        void flashSaleAllowsExactlyTwoUnits() {
            service.registerProduct("FLASH-1", ProductCategory.FLASH_SALE);
            service.addStock("FLASH-1", 10);
            service.reserve("ORDER-1", "FLASH-1", 2);
            assertEquals(8, service.available("FLASH-1"));
        }

        @Test
        void standardHasNoOrderLimit() {
            service.addStock("SKU-1", 100);
            service.reserve("ORDER-1", "SKU-1", 100);
            assertEquals(0, service.available("SKU-1"));
        }

        @Test
        void preOrderHasNoOrderLimit() {
            service.registerProduct("PRE-1", ProductCategory.PRE_ORDER);
            service.addStock("PRE-1", 50);
            service.reserve("ORDER-1", "PRE-1", 50);
            assertEquals(0, service.available("PRE-1"));
        }
    }

    @Nested
    class StockAlerts {

        @Test
        void alertFiresWhenAvailableDropsToFiveOrBelow() {
            List<Integer> alerts = new ArrayList<>();
            InventoryService svc = Inventory.create(Clock.systemUTC(), (sku, n) -> alerts.add(n));
            svc.registerProduct("SKU-A", ProductCategory.STANDARD);
            svc.addStock("SKU-A", 10);
            svc.reserve("ORDER-1", "SKU-A", 5);
            assertEquals(1, alerts.size());
            assertEquals(5, alerts.get(0));
        }

        @Test
        void alertDoesNotFireWhenAboveThreshold() {
            List<String> alerts = new ArrayList<>();
            InventoryService svc = Inventory.create(Clock.systemUTC(), (sku, n) -> alerts.add(sku));
            svc.registerProduct("SKU-A", ProductCategory.STANDARD);
            svc.addStock("SKU-A", 10);
            svc.reserve("ORDER-1", "SKU-A", 4);
            assertEquals(0, alerts.size());
        }

        @Test
        void alertFiresOnlyOnceUntilRestock() {
            List<String> alerts = new ArrayList<>();
            InventoryService svc = Inventory.create(Clock.systemUTC(), (sku, n) -> alerts.add(sku));
            svc.registerProduct("SKU-A", ProductCategory.STANDARD);
            svc.addStock("SKU-A", 10);
            svc.reserve("ORDER-1", "SKU-A", 6);
            svc.reserve("ORDER-2", "SKU-A", 1);
            svc.reserve("ORDER-3", "SKU-A", 1);
            assertEquals(1, alerts.size());
        }

        @Test
        void alertResetsAfterRestock() {
            List<String> alerts = new ArrayList<>();
            InventoryService svc = Inventory.create(Clock.systemUTC(), (sku, n) -> alerts.add(sku));
            svc.registerProduct("SKU-A", ProductCategory.STANDARD);
            svc.addStock("SKU-A", 10);
            svc.reserve("ORDER-1", "SKU-A", 6);
            svc.addStock("SKU-A", 4);
            svc.reserve("ORDER-2", "SKU-A", 4);
            assertEquals(2, alerts.size());
        }

        @Test
        void alertIncludesCorrectAvailableCount() {
            List<int[]> captured = new ArrayList<>();
            InventoryService svc = Inventory.create(Clock.systemUTC(), (sku, n) -> captured.add(new int[]{n}));
            svc.registerProduct("SKU-A", ProductCategory.STANDARD);
            svc.addStock("SKU-A", 8);
            svc.reserve("ORDER-1", "SKU-A", 5);
            assertEquals(1, captured.size());
            assertEquals(3, captured.get(0)[0]);
        }
    }

    @Nested
    class Validations {

        @Test
        void availableReturnsZeroForUnknownProduct() {
            assertEquals(0, service.available("NO-EXISTE"));
        }

        @Test
        void reservingZeroQuantityThrows() {
            service.addStock("SKU-1", 5);
            assertThrows(IllegalArgumentException.class,
                    () -> service.reserve("ORDER-1", "SKU-1", 0));
        }

        @Test
        void addingZeroStockThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> service.addStock("SKU-1", 0));
        }

        @Test
        void addingNegativeStockThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> service.addStock("SKU-1", -5));
        }

        @Test
        void confirmWithNoReservationThrows() {
            assertThrows(IllegalStateException.class,
                    () -> service.confirm("ORDER-INEXISTENTE"));
        }

        @Test
        void addStockOnUnregisteredProductThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> service.addStock("SKU-FAKE", 10));
        }

        @Test
        void reserveOnUnregisteredProductThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> service.reserve("ORDER-1", "SKU-FAKE", 1));
        }

        @Test
        void multipleOrdersReserveIndependently() {
            service.addStock("SKU-1", 10);
            service.reserve("ORDER-1", "SKU-1", 3);
            service.reserve("ORDER-2", "SKU-1", 4);
            assertEquals(3, service.available("SKU-1"));
        }

        @Test
        void confirmOneOrderDoesNotAffectOther() {
            service.addStock("SKU-1", 10);
            service.reserve("ORDER-1", "SKU-1", 3);
            service.reserve("ORDER-2", "SKU-1", 4);
            service.confirm("ORDER-1");
            assertEquals(3, service.available("SKU-1"));
        }
    }

    static class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant initial) {
            this.now = initial;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() { return now; }

        @Override
        public ZoneOffset getZone() { return ZoneOffset.UTC; }

        @Override
        public Clock withZone(java.time.ZoneId zone) { return this; }
    }
}