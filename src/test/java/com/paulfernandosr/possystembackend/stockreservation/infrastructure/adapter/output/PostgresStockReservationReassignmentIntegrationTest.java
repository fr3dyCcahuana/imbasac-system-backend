package com.paulfernandosr.possystembackend.stockreservation.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output.PostgresProductStockRepository;
import com.paulfernandosr.possystembackend.product.infrastructure.adapter.output.PostgresProductStockValidationRepository;
import com.paulfernandosr.possystembackend.stockreservation.domain.exception.StockReservationException;
import com.paulfernandosr.possystembackend.stockreservation.domain.model.StockReservationReassignment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs only against an explicitly supplied disposable local database. Every test owns a random
 * schema; no application properties, Spring context, existing schema, or external service is read.
 */
@EnabledIfEnvironmentVariable(named = "STOCK_RESERVATION_TEST_JDBC_URL",
        matches = "jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/stock_reservation_test")
class PostgresStockReservationReassignmentIntegrationTest {
    private DriverManagerDataSource administrativeDataSource;
    private JdbcClient jdbc;
    private TransactionTemplate transaction;
    private PostgresProductStockReservationRepository reservations;
    private PostgresProductStockRepository stock;
    private String schema;

    @BeforeEach
    void createIsolatedSchema() {
        String url = System.getenv("STOCK_RESERVATION_TEST_JDBC_URL");
        String password = System.getenv("STOCK_RESERVATION_TEST_PASSWORD");
        administrativeDataSource = new DriverManagerDataSource(url, "postgres", password);
        schema = "stock_test_" + UUID.randomUUID().toString().replace("-", "");
        new JdbcTemplate(administrativeDataSource).execute("CREATE SCHEMA " + schema);
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                url + "?currentSchema=" + schema + "&options=-c%20lock_timeout%3D5000", "postgres", password);
        jdbc = JdbcClient.create(dataSource);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        reservations = new PostgresProductStockReservationRepository(jdbc);
        stock = new PostgresProductStockRepository(jdbc);
        new JdbcTemplate(dataSource).execute("""
                CREATE TABLE product (
                    id BIGINT PRIMARY KEY, sku TEXT, affects_stock BOOLEAN NOT NULL DEFAULT TRUE,
                    manage_by_serial BOOLEAN NOT NULL DEFAULT FALSE, category TEXT DEFAULT 'ACCESORIOS',
                    gift_allowed BOOLEAN NOT NULL DEFAULT TRUE, facturable_sunat BOOLEAN NOT NULL DEFAULT TRUE
                );
                CREATE TABLE product_stock (
                    product_id BIGINT PRIMARY KEY REFERENCES product(id),
                    quantity_on_hand NUMERIC(18,3) NOT NULL,
                    average_cost NUMERIC(18,4) NOT NULL DEFAULT 1,
                    last_unit_cost NUMERIC(18,4) NOT NULL DEFAULT 1,
                    last_movement_at TIMESTAMPTZ
                );
                CREATE TABLE proforma (
                    id BIGINT PRIMARY KEY, series TEXT NOT NULL DEFAULT 'PF',
                    number BIGINT, status TEXT NOT NULL DEFAULT 'PENDIENTE'
                );
                CREATE TABLE sale (id BIGINT PRIMARY KEY);
                CREATE TABLE users (id BIGINT PRIMARY KEY, username TEXT, first_name TEXT, last_name TEXT);
                CREATE TABLE product_serial_unit (
                    id BIGSERIAL PRIMARY KEY, product_id BIGINT REFERENCES product(id), status TEXT
                );
                CREATE TABLE proforma_item (
                    id BIGINT PRIMARY KEY, proforma_id BIGINT REFERENCES proforma(id),
                    product_id BIGINT REFERENCES product(id), sku TEXT,
                    quantity NUMERIC(18,3) NOT NULL, affects_stock BOOLEAN NOT NULL DEFAULT TRUE
                );
                CREATE TABLE product_stock_reservation (
                    id BIGSERIAL PRIMARY KEY, product_id BIGINT NOT NULL REFERENCES product(id),
                    proforma_id BIGINT NOT NULL REFERENCES proforma(id),
                    proforma_item_id BIGINT REFERENCES proforma_item(id) ON DELETE SET NULL,
                    quantity NUMERIC(18,3) NOT NULL CHECK (quantity > 0),
                    reserved_date DATE NOT NULL DEFAULT CURRENT_DATE,
                    expires_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_DATE + INTERVAL '1 day',
                    status TEXT NOT NULL DEFAULT 'ACTIVE', reserved_by BIGINT,
                    consumed_at TIMESTAMPTZ, released_at TIMESTAMPTZ,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                );
                """);
        new ResourceDatabasePopulator(new ClassPathResource(
                "db/scripts/20261022_stock_reservation_reassignment.sql")).execute(dataSource);
    }

    @AfterEach
    void dropOnlyOwnedSchema() {
        if (schema != null && schema.matches("stock_test_[a-f0-9]{32}")) {
            new JdbcTemplate(administrativeDataSource).execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    @Test
    void usesOwnAndFreeStockBeforeTouchingAnotherProforma() {
        seed(10, 2, 6);
        transaction.executeWithoutResult(status -> {
            prepare(4, 1L, List.of());
            stock.decreaseOnHandOrFail(1L, decimal(4));
        });

        assertThat(quantity("product_stock", "quantity_on_hand", "product_id = 1")).isEqualByComparingTo("6");
        assertThat(reservationStatus(1)).isEqualTo("CONSUMED");
        assertThat(reservationQuantity(2)).isEqualByComparingTo("6");
        assertThat(auditCount()).isZero();
    }

    @Test
    void missingAuditMigrationRejectsBeforeAnyReservationMutation() {
        seed(10, 2, 8);
        jdbc.sql("DROP TABLE product_stock_reservation_reassignment").update();

        // Deliberately without an outer transaction: the readiness check must precede all updates.
        assertThatThrownBy(() -> prepare(5, 1L, List.of(selection(2, 3))))
                .isInstanceOf(StockReservationException.class)
                .hasMessageContaining("falta completar una actualización del sistema");

        assertThat(reservationQuantity(1)).isEqualByComparingTo("2");
        assertThat(reservationQuantity(2)).isEqualByComparingTo("8");
        assertThat(reservationStatus(1)).isEqualTo("ACTIVE");
        assertThat(reservationStatus(2)).isEqualTo("ACTIVE");
        assertThat(quantity("product_stock", "quantity_on_hand", "product_id = 1")).isEqualByComparingTo("10");
    }

    @Test
    void ordinarySaleCanUseOwnAndFreeStockWithoutAuditMigration() {
        seed(10, 2, 6);
        jdbc.sql("DROP TABLE product_stock_reservation_reassignment").update();

        transaction.executeWithoutResult(status -> {
            prepare(4, 1L, List.of());
            stock.decreaseOnHandOrFail(1L, decimal(4));
        });

        assertThat(reservationStatus(1)).isEqualTo("CONSUMED");
        assertThat(reservationStatus(2)).isEqualTo("ACTIVE");
        assertThat(reservationQuantity(2)).isEqualByComparingTo("6");
        assertThat(quantity("product_stock", "quantity_on_hand", "product_id = 1")).isEqualByComparingTo("6");
    }

    @Test
    void partiallyReassignsOnlyDeficitAndPreservesOriginalProformaItems() {
        seed(10, 2, 8);
        transaction.executeWithoutResult(status -> {
            prepare(5, 1L, List.of(selection(2, 3)));
            stock.decreaseOnHandOrFail(1L, decimal(5));
        });

        assertThat(reservationQuantity(2)).isEqualByComparingTo("5");
        assertThat(reservationStatus(2)).isEqualTo("ACTIVE");
        assertThat(reservationStatus(1)).isEqualTo("CONSUMED");
        assertThat(quantity("proforma_item", "quantity", "id = 2")).isEqualByComparingTo("8");
        assertThat(quantity("product_stock", "quantity_on_hand", "product_id = 1")).isEqualByComparingTo("5");
        assertThat(auditCount()).isEqualTo(1);
        assertThat(quantity("product_stock_reservation_reassignment", "quantity", "reservation_id = 2"))
                .isEqualByComparingTo("3");
        assertThat(quantity("product_stock_reservation_reassignment", "quantity_before", "reservation_id = 2"))
                .isEqualByComparingTo("8");
        assertThat(quantity("product_stock_reservation_reassignment", "quantity_after", "reservation_id = 2"))
                .isEqualByComparingTo("5");
        assertThat(jdbc.sql("SELECT reassigned_by FROM product_stock_reservation_reassignment")
                .query(Long.class).single()).isEqualTo(77L);
        assertThat(jdbc.sql("SELECT status FROM proforma WHERE id = 2").query(String.class).single())
                .isEqualTo("PENDIENTE");
    }

    @Test
    void fullyUsedDonorIsReleasedAndAuditedWithZeroRemainder() {
        seed(10, 2, 8);
        transaction.executeWithoutResult(status -> {
            prepare(10, 1L, List.of(selection(2, 8)));
            stock.decreaseOnHandOrFail(1L, decimal(10));
        });

        assertThat(reservationStatus(2)).isEqualTo("RELEASED");
        assertThat(quantity("product_stock_reservation_reassignment", "quantity_after", "reservation_id = 2"))
                .isEqualByComparingTo("0");
        assertThat(quantity("product_stock", "quantity_on_hand", "product_id = 1")).isEqualByComparingTo("0");
    }

    @Test
    void rejectsPhysicalShortageEvenWhenAllReservationsWereConfirmed() {
        seed(10, 2, 8);
        assertRejected(11, 1L, List.of(selection(2, 8)));
        assertUntouched();
    }

    @Test
    void requiresExactDeficitForConfirmationAndNeverReleasesExtraStock() {
        seed(10, 2, 8);
        assertRejected(5, 1L, List.of());
        assertRejected(5, 1L, List.of(selection(2, 2)));
        assertRejected(5, 1L, List.of(selection(2, 4)));
        assertUntouched();
    }

    @Test
    void rejectsOwnDuplicateAndUnrelatedReservations() {
        seed(10, 2, 8);
        assertRejected(5, 1L, List.of(selection(1, 2)));
        assertRejected(5, 1L, List.of(selection(2, 1), selection(2, 2)));
        assertRejected(5, 1L, List.of(selection(999, 3)));
        assertUntouched();
    }

    @Test
    void rejectsExpiredReleasedReducedAndCancelledDonorSelections() {
        seed(10, 2, 8);
        jdbc.sql("UPDATE product_stock_reservation SET reserved_date = CURRENT_DATE - 1 WHERE id = 2").update();
        assertRejected(5, 1L, List.of(selection(2, 3)));
        jdbc.sql("UPDATE product_stock_reservation SET reserved_date = CURRENT_DATE, status = 'RELEASED' WHERE id = 2")
                .update();
        assertRejected(5, 1L, List.of(selection(2, 3)));
        jdbc.sql("UPDATE product_stock_reservation SET status = 'ACTIVE', quantity = 2 WHERE id = 2").update();
        assertRejected(5, 1L, List.of(selection(2, 3)));
        jdbc.sql("UPDATE product_stock_reservation SET quantity = 8 WHERE id = 2").update();
        jdbc.sql("UPDATE proforma SET status = 'ANULADA' WHERE id = 2").update();
        assertRejected(5, 1L, List.of(selection(2, 3)));
        assertUntouched();
    }

    @Test
    void directSaleCanTakeReservationsWithoutSourceProforma() {
        seed(10, 2, 8);
        transaction.executeWithoutResult(status -> {
            prepare(3, null, List.of(selection(2, 3)));
            stock.decreaseOnHandOrFail(1L, decimal(3));
        });

        assertThat(reservationStatus(1)).isEqualTo("ACTIVE");
        assertThat(reservationQuantity(2)).isEqualByComparingTo("5");
        assertThat(quantity("product_stock", "quantity_on_hand", "product_id = 1")).isEqualByComparingTo("7");
        assertThat(jdbc.sql("SELECT COUNT(*) FROM product_stock_reservation_reassignment WHERE source_proforma_id IS NULL")
                .query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void validationShowsOtherProformaSellerAndCurrentReservedQuantity() {
        seed(10, 2, 8);
        PostgresProductStockValidationRepository validation = new PostgresProductStockValidationRepository(jdbc);
        var before = validation.validate(List.of(1L), false, 50, 101L).iterator().next();
        assertThat(before.getStockOnHand()).isEqualByComparingTo("10");
        assertThat(before.getStockAvailable()).isEqualByComparingTo("2");
        assertThat(before.getStockReservedBySource()).isEqualByComparingTo("2");
        assertThat(before.getStockReserved()).isEqualByComparingTo("8");
        assertThat(before.getStockReservations()).hasSize(1);
        assertThat(before.getStockReservations().getFirst().getReservationId()).isEqualTo(2L);
        assertThat(before.getStockReservations().getFirst().getProformaNumber()).isEqualTo(102L);
        assertThat(before.getStockReservations().getFirst().getReservedByName()).isEqualTo("Carlos Pérez");
        assertThat(before.getStockReservations().getFirst().getExpiresAt()).isNotNull();

        transaction.executeWithoutResult(status -> {
            prepare(5, 1L, List.of(selection(2, 3)));
            stock.decreaseOnHandOrFail(1L, decimal(5));
        });
        var after = validation.validate(List.of(1L), false, 50, null).iterator().next();
        assertThat(after.getStockOnHand()).isEqualByComparingTo("5");
        assertThat(after.getStockAvailable()).isEqualByComparingTo("0");
        assertThat(after.getStockReservedBySource()).isEqualByComparingTo("0");
        assertThat(after.getStockReservations()).hasSize(1);
        assertThat(after.getStockReservations().getFirst().getQuantity()).isEqualByComparingTo("5");
    }

    @Test
    void auditSurvivesDonorReservationAndItemReplacement() {
        seed(10, 2, 8);
        transaction.executeWithoutResult(status -> {
            prepare(5, 1L, List.of(selection(2, 3)));
            stock.decreaseOnHandOrFail(1L, decimal(5));
        });
        jdbc.sql("DELETE FROM product_stock_reservation WHERE id = 2").update();
        jdbc.sql("DELETE FROM proforma_item WHERE id = 2").update();
        assertThat(auditCount()).isEqualTo(1);
        assertThat(quantity("product_stock_reservation_reassignment", "quantity_before", "reservation_id = 2"))
                .isEqualByComparingTo("8");
    }

    @Test
    void laterFailureRollsBackReassignmentOwnConsumptionAuditAndPhysicalStock() {
        seed(10, 2, 8);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            prepare(5, 1L, List.of(selection(2, 3)));
            stock.decreaseOnHandOrFail(1L, decimal(5));
            throw new IllegalStateException("subsequent sale persistence failed");
        })).isInstanceOf(IllegalStateException.class);
        assertUntouched();
    }

    @Test
    @Timeout(20)
    void simultaneousSaleWaitsForStockAndThenRechecksPhysicalExistence() throws Exception {
        seed(10, 2, 8);
        withSaleHoldingStock(() -> {
            prepare(6, null, List.of(selection(2, 5)));
            stock.decreaseOnHandOrFail(1L, decimal(6));
        });

        assertThat(quantity("product_stock", "quantity_on_hand", "product_id = 1")).isEqualByComparingTo("5");
        assertThat(reservationQuantity(2)).isEqualByComparingTo("5");
        assertThat(auditCount()).isEqualTo(1);
    }

    @Test
    @Timeout(20)
    void newProformaWaitsForSaleAndCannotReserveUnitsAlreadySoldOrReserved() throws Exception {
        seed(10, 2, 8);
        jdbc.sql("INSERT INTO proforma(id, number) VALUES (3, 103)").update();
        jdbc.sql("INSERT INTO proforma_item(id, proforma_id, product_id, sku, quantity) VALUES (3, 3, 1, 'SKU-1', 1)")
                .update();
        withSaleHoldingStock(() -> reservations.replaceActiveForProforma(3L, 88L));

        assertThat(jdbc.sql("SELECT COUNT(*) FROM product_stock_reservation WHERE proforma_id = 3")
                .query(Long.class).single()).isZero();
        assertThat(quantity("product_stock", "quantity_on_hand", "product_id = 1")).isEqualByComparingTo("5");
    }

    @Test
    @Timeout(15)
    void editedDonorFailsPromptlyInsteadOfDeadlockingWithProductLock() throws Exception {
        seed(10, 2, 8);
        CountDownLatch donorLocked = new CountDownLatch(1);
        CountDownLatch releaseDonor = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> editor = executor.submit(() -> transaction.executeWithoutResult(status -> {
            jdbc.sql("SELECT id FROM proforma WHERE id = 2 FOR UPDATE").query(Long.class).single();
            donorLocked.countDown();
            await(releaseDonor);
        }));
        try {
            assertThat(donorLocked.await(5, TimeUnit.SECONDS)).isTrue();
            assertRejected(5, 1L, List.of(selection(2, 3)));
            assertUntouched();
        } finally {
            releaseDonor.countDown();
            editor.get(5, TimeUnit.SECONDS);
            executor.shutdownNow();
        }
    }

    private void withSaleHoldingStock(Runnable competingOperation) throws Exception {
        CountDownLatch salePrepared = new CountDownLatch(1);
        CountDownLatch commitSale = new CountDownLatch(1);
        CountDownLatch competitorStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<?> first = executor.submit(() -> transaction.executeWithoutResult(status -> {
            prepare(5, 1L, List.of(selection(2, 3)));
            stock.decreaseOnHandOrFail(1L, decimal(5));
            salePrepared.countDown();
            await(commitSale);
        }));
        Future<?> competitor = null;
        try {
            assertThat(salePrepared.await(5, TimeUnit.SECONDS)).isTrue();
            competitor = executor.submit(() -> {
                competitorStarted.countDown();
                assertThatThrownBy(() -> transaction.executeWithoutResult(status -> competingOperation.run()))
                        .isInstanceOf(StockReservationException.class);
            });
            assertThat(competitorStarted.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> blockedCompetitor = competitor;
            assertThatThrownBy(() -> blockedCompetitor.get(250, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            commitSale.countDown();
            first.get(5, TimeUnit.SECONDS);
            competitor.get(5, TimeUnit.SECONDS);
        } finally {
            commitSale.countDown();
            executor.shutdownNow();
        }
    }

    private void seed(int physical, int own, int donor) {
        jdbc.sql("INSERT INTO users(id, username, first_name, last_name) VALUES (11, 'ana', 'Ana', 'Soto'), (22, 'carlos', 'Carlos', 'Pérez'), (77, 'actor', 'Actor', 'Venta')")
                .update();
        jdbc.sql("INSERT INTO sale(id) VALUES (900)").update();
        jdbc.sql("INSERT INTO product(id, sku) VALUES (1, 'SKU-1')").update();
        jdbc.sql("INSERT INTO product_stock(product_id, quantity_on_hand) VALUES (1, ?)").param(physical).update();
        jdbc.sql("INSERT INTO proforma(id, number) VALUES (1, 101), (2, 102)").update();
        jdbc.sql("INSERT INTO proforma_item(id, proforma_id, product_id, sku, quantity) VALUES (1, 1, 1, 'SKU-1', ?), (2, 2, 1, 'SKU-1', ?)")
                .params(own, donor).update();
        jdbc.sql("INSERT INTO product_stock_reservation(id, product_id, proforma_id, proforma_item_id, quantity, reserved_by) VALUES (1, 1, 1, 1, ?, 11), (2, 1, 2, 2, ?, 22)")
                .params(own, donor).update();
    }

    private void prepare(int requested, Long source, List<StockReservationReassignment> selections) {
        reservations.prepareStockForSale(Map.of(1L, decimal(requested)), source, selections, 900L, 77L);
    }

    private void assertRejected(int requested, Long source, List<StockReservationReassignment> selections) {
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> prepare(requested, source, selections)))
                .isInstanceOf(StockReservationException.class);
    }

    private void assertUntouched() {
        assertThat(reservationQuantity(1)).isEqualByComparingTo("2");
        assertThat(reservationQuantity(2)).isEqualByComparingTo("8");
        assertThat(reservationStatus(1)).isEqualTo("ACTIVE");
        assertThat(quantity("product_stock", "quantity_on_hand", "product_id = 1")).isEqualByComparingTo("10");
        assertThat(auditCount()).isZero();
    }

    private BigDecimal reservationQuantity(long id) {
        return quantity("product_stock_reservation", "quantity", "id = " + id);
    }

    private String reservationStatus(long id) {
        return jdbc.sql("SELECT status FROM product_stock_reservation WHERE id = ?").param(id).query(String.class).single();
    }

    private long auditCount() {
        return jdbc.sql("SELECT COUNT(*) FROM product_stock_reservation_reassignment").query(Long.class).single();
    }

    private BigDecimal quantity(String table, String column, String where) {
        return jdbc.sql("SELECT " + column + " FROM " + table + " WHERE " + where).query(BigDecimal.class).single();
    }

    private static StockReservationReassignment selection(long id, int quantity) {
        return new StockReservationReassignment(id, decimal(quantity));
    }

    private static BigDecimal decimal(int value) {
        return BigDecimal.valueOf(value);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(8, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent test did not release transaction");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
