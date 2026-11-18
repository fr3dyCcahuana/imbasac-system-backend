package com.paulfernandosr.possystembackend.reports.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.reports.domain.SlowMovingFilter;
import com.paulfernandosr.possystembackend.reports.domain.SlowMovingProduct;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs only against a disposable local database; never uses application profiles or credentials. */
@EnabledIfEnvironmentVariable(named = "ROTATION_REPORT_TEST_JDBC_URL",
        matches = "jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/rotation_report_test")
class PostgresSlowMovingProductsRepositoryTest {
    private JdbcTemplate admin;
    private JdbcTemplate db;
    private String schema;
    private PostgresSlowMovingProductsRepository repository;

    @BeforeEach
    void isolatedSchema() {
        String url = System.getenv("ROTATION_REPORT_TEST_JDBC_URL");
        admin = new JdbcTemplate(new DriverManagerDataSource(url, "postgres", ""));
        schema = "rotation_test_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("CREATE SCHEMA " + schema);
        var source = new DriverManagerDataSource(url + "?currentSchema=" + schema, "postgres", "");
        db = new JdbcTemplate(source);
        repository = new PostgresSlowMovingProductsRepository(JdbcClient.create(source));
        db.execute("""
                CREATE TABLE product (id bigint PRIMARY KEY, sku text, name text, model text, created_at timestamp DEFAULT '2024-01-01',
                  affects_stock boolean DEFAULT true, manage_by_serial boolean DEFAULT false,
                  price_a numeric DEFAULT 10, price_b numeric DEFAULT 9, price_c numeric DEFAULT 8, price_d numeric DEFAULT 7);
                CREATE TABLE product_stock (product_id bigint, quantity_on_hand numeric);
                CREATE TABLE product_serial_unit (product_id bigint, status text);
                CREATE TABLE product_stock_movement (product_id bigint, created_at timestamp, quantity_in numeric, movement_type text);
                CREATE TABLE counter_sale (id bigint PRIMARY KEY, issue_date date, status text, currency text DEFAULT 'PEN', exchange_rate numeric);
                CREATE TABLE counter_sale_item (counter_sale_id bigint, product_id bigint, quantity numeric,
                  revenue_total numeric, line_kind text DEFAULT 'VENDIDO', affects_stock boolean DEFAULT true);
                CREATE TABLE sale (id bigint PRIMARY KEY, issue_date date, status text, currency text DEFAULT 'PEN', exchange_rate numeric);
                CREATE TABLE sale_item (sale_id bigint, product_id bigint, quantity numeric, revenue_total numeric,
                  line_kind text DEFAULT 'VENDIDO', affects_stock boolean DEFAULT true);
                CREATE TABLE proforma (id bigint PRIMARY KEY, converted_sale_id bigint, status text);
                CREATE TABLE proforma_item (proforma_id bigint, product_id bigint, quantity numeric, line_subtotal numeric,
                  facturable_sunat boolean, affects_stock boolean DEFAULT true);
                CREATE TABLE credit_note (id bigint PRIMARY KEY, sale_id bigint, issue_date date, status text);
                CREATE TABLE credit_note_item (credit_note_id bigint, product_id bigint, quantity numeric, returned_to_stock boolean);
                INSERT INTO product (id, sku, name, model) VALUES
                  (1, 'NUNCA', 'Nunca vendido', 'PULSAR'), (2, 'ANTIGUO', 'Venta antigua', 'PULSAR'),
                  (3, 'VENDIDO', 'Venta comercial', 'PULSAR'), (4, 'OTRO', 'Otro modelo', 'PULSAR 135'),
                  (5, 'NUEVO', 'Ingreso reciente', 'PULSAR'), (6, 'SIN-STOCK', 'Sin existencias', 'PULSAR'),
                  (7, 'SERIE', 'Con serial', 'PULSAR'), (8, 'DEVOLUCION', 'Con devolución', 'PULSAR'),
                  (9, 'PROFORMA', 'Interno de proforma', 'PULSAR'), (10, 'DIVISA', 'Dólares sin tipo de cambio', 'PULSAR'),
                  (11, 'POSTERIOR', 'Producto creado después del cierre', 'PULSAR');
                UPDATE product SET created_at = '2026-11-01' WHERE id = 11;
                INSERT INTO product_stock SELECT id, CASE WHEN id = 6 THEN 0 ELSE 100 END FROM product;
                UPDATE product SET manage_by_serial = true WHERE id = 7;
                INSERT INTO product_serial_unit VALUES (7, 'EN_ALMACEN'), (7, 'EN_ALMACEN'), (7, 'VENDIDO');
                INSERT INTO product_stock_movement VALUES
                  (1, '2025-01-01', 100, 'IN_PURCHASE'), (2, '2025-01-01', 100, 'IN_ADJUST'),
                  (5, '2026-10-20', 100, 'IN_PURCHASE');
                INSERT INTO counter_sale (id, issue_date, status) VALUES
                  (1, '2025-02-01', 'EMITIDA'), (2, '2026-10-10', 'EMITIDA'),
                  (3, '2026-10-15', 'EMITIDA'), (4, '2026-10-11', 'ANULADA'), (5, '2026-11-01', 'EMITIDA');
                INSERT INTO counter_sale_item (counter_sale_id, product_id, quantity, revenue_total) VALUES
                  (1, 2, 2, 20), (2, 3, 3, 30), (2, 3, 2, 20), (3, 3, 1, 10), (4, 1, 10, 100), (5, 1, 10, 100);
                INSERT INTO counter_sale_item VALUES (2, 1, 1, 0, 'OBSEQUIO', true);
                INSERT INTO sale (id, issue_date, status) VALUES
                  (1, '2026-10-12', 'EMITIDA'), (2, '2026-10-13', 'EMITIDA'),
                  (3, '2026-10-14', 'ANULADA'), (4, '2026-10-16', 'EMITIDA');
                INSERT INTO sale (id, issue_date, status, currency, exchange_rate) VALUES
                  (5, '2026-10-17', 'EMITIDA', 'USD', 3.5), (6, '2026-10-17', 'EMITIDA', 'USD', NULL);
                INSERT INTO sale_item VALUES
                  (1, 3, 6, 60, 'VENDIDO', false), -- Fiscal copy of counter sales, never double count.
                  (2, 8, 5, 50, 'VENDIDO', true), (3, 1, 10, 100, 'VENDIDO', true),
                  (4, 9, 1, 10, 'VENDIDO', true), (5, 8, 2, 4, 'VENDIDO', true),
                  (6, 10, 1, 3, 'VENDIDO', true);
                INSERT INTO proforma VALUES (1, 4, 'CONVERTIDA'), (2, NULL, 'VIGENTE');
                INSERT INTO proforma_item VALUES (1, 9, 2, 20, false, true), (1, 9, 1, 10, true, true), (2, 1, 10, 100, false, true);
                INSERT INTO credit_note VALUES (1, 2, '2026-10-18', 'EMITIDA'), (2, 2, '2026-10-19', 'ANULADA'), (3, 2, '2026-10-18', 'EMITIDA');
                INSERT INTO credit_note_item VALUES (1, 8, 2, true), (2, 8, 3, true), (3, 8, 1, false);
                """);
    }

    @AfterEach
    void dropOnlyOwnedSchema() {
        if (schema != null && schema.matches("rotation_test_[a-f0-9]{32}")) admin.execute("DROP SCHEMA " + schema + " CASCADE");
    }

    @Test
    void neverSoldUsesAllHistoryAndKeepsZerosVoidsAndGiftsOutOfSales() {
        var rows = repository.find(filter(SlowMovingFilter.Status.NEVER_SOLD, SlowMovingFilter.Sort.SALES, true), null);
        assertThat(rows).extracting(SlowMovingProduct::productId).containsExactly(1L, 5L, 7L);
        var never = rows.getFirst();
        assertThat(never.historicalSales()).isZero();
        assertThat(never.lastSaleDate()).isNull();
        assertThat(never.averageSalePrice()).isNull();
        assertThat(never.firstStockDate()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(rows.get(1).newInPeriod()).isTrue();
        assertThat(rows.get(2).firstStockDate()).isNull();
        assertThat(rows.get(2).stock()).isEqualByComparingTo("2");
    }

    @Test
    void noSalesInPeriodIncludesProductsThatSoldPreviouslyAndModelIsExact() {
        var rows = repository.find(filter(SlowMovingFilter.Status.NO_SALES, SlowMovingFilter.Sort.SALES, true), null);
        assertThat(rows).extracting(SlowMovingProduct::productId).contains(1L, 2L).doesNotContain(4L, 6L, 11L);
        var old = rows.stream().filter(p -> p.productId() == 2).findFirst().orElseThrow();
        assertThat(old.countSales()).isZero();
        assertThat(old.historicalSales()).isEqualTo(1);
        assertThat(old.lastSaleDate()).isEqualTo(LocalDate.of(2025, 2, 1));
        assertThat(old.daysWithoutSale()).isEqualTo(635);
        assertThat(repository.findModels()).containsExactly("PULSAR", "PULSAR 135");
    }

    @Test
    void countsCommercialOperationsOnceWithoutSunatDependencyAndIncludesInternalConvertedLines() {
        var rows = repository.find(filter(SlowMovingFilter.Status.ALL, SlowMovingFilter.Sort.SALES, false), null);
        var counter = rows.stream().filter(p -> p.productId() == 3).findFirst().orElseThrow();
        assertThat(counter.countSales()).isEqualTo(2);
        assertThat(counter.soldQty()).isEqualByComparingTo("6");
        var converted = rows.stream().filter(p -> p.productId() == 9).findFirst().orElseThrow();
        assertThat(converted.countSales()).isEqualTo(1);
        assertThat(converted.soldQty()).isEqualByComparingTo("3");
        assertThat(rows).extracting(SlowMovingProduct::productId).contains(6L);
        assertThat(repository.find(filter(SlowMovingFilter.Status.ALL, SlowMovingFilter.Sort.SALES, false), 2)).hasSize(2);
    }

    @Test
    void deductsOnlyEffectiveStockReturnsAndConvertsAveragePricesToSoles() {
        var rows = repository.find(filter(SlowMovingFilter.Status.ALL, SlowMovingFilter.Sort.QTY, true), null);
        var returned = rows.stream().filter(p -> p.productId() == 8).findFirst().orElseThrow();
        assertThat(returned.soldQty()).isEqualByComparingTo("7");
        assertThat(returned.returnedQty()).isEqualByComparingTo("2");
        assertThat(returned.netQty()).isEqualByComparingTo("5");
        assertThat(returned.averageSalePrice()).isCloseTo(new java.math.BigDecimal("9.142857142857143"),
                org.assertj.core.data.Offset.offset(new java.math.BigDecimal("0.000000000000001")));
        assertThat(rows.stream().filter(p -> p.productId() == 10).findFirst().orElseThrow().averageSalePrice()).isNull();
    }

    private SlowMovingFilter filter(SlowMovingFilter.Status status, SlowMovingFilter.Sort sort, boolean stockOnly) {
        return new SlowMovingFilter(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 29), "PULSAR", status, sort, stockOnly);
    }
}
