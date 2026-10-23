package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.paulfernandosr.possystembackend.salev2.application.CreateIdempotentSaleV2Service;
import com.paulfernandosr.possystembackend.salev2.domain.exception.InvalidSaleV2Exception;
import com.paulfernandosr.possystembackend.salev2.domain.exception.SaleCreateRejectedException;
import com.paulfernandosr.possystembackend.salev2.domain.exception.SaleIdempotencyConflictException;
import com.paulfernandosr.possystembackend.salev2.domain.model.DocType;
import com.paulfernandosr.possystembackend.salev2.domain.port.input.CreateSaleV2UseCase;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2CreateRequest;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2DocumentResponse;
import com.paulfernandosr.possystembackend.user.domain.User;
import com.paulfernandosr.possystembackend.user.domain.port.output.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Disposable database only. Each test creates and removes its own randomly named schema. */
@EnabledIfEnvironmentVariable(named = "SALE_IDEMPOTENCY_TEST_JDBC_URL",
        matches = "jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/sale_idempotency_test")
class PostgresSaleCreateIdempotencyIntegrationTest {
    private DriverManagerDataSource administrativeDataSource;
    private DriverManagerDataSource dataSource;
    private JdbcClient jdbc;
    private PostgresSaleCreateIdempotencyRepository repository;
    private DataSourceTransactionManager manager;
    private CreateIdempotentSaleV2Service service;
    private CreateSaleV2UseCase createSale;
    private String schema;
    private final AtomicInteger executions = new AtomicInteger();

    @BeforeEach
    void isolatedDatabaseSchema() {
        String url = System.getenv("SALE_IDEMPOTENCY_TEST_JDBC_URL");
        String password = System.getenv("SALE_IDEMPOTENCY_TEST_PASSWORD");
        administrativeDataSource = new DriverManagerDataSource(url, "postgres", password);
        schema = "sale_idem_test_" + UUID.randomUUID().toString().replace("-", "");
        new JdbcTemplate(administrativeDataSource).execute("CREATE SCHEMA " + schema);
        dataSource = new DriverManagerDataSource(url + "?currentSchema=" + schema
                + "&options=-c%20lock_timeout%3D5000", "postgres", password);
        jdbc = JdbcClient.create(dataSource);
        manager = new DataSourceTransactionManager(dataSource);
        repository = new PostgresSaleCreateIdempotencyRepository(jdbc);
        new JdbcTemplate(dataSource).execute("""
                CREATE TABLE users (id BIGINT PRIMARY KEY);
                INSERT INTO users VALUES (1), (2);
                CREATE TABLE sale (id BIGSERIAL PRIMARY KEY, total NUMERIC(18,4));
                CREATE TABLE test_stock (id BIGINT PRIMARY KEY, quantity NUMERIC(18,3));
                INSERT INTO test_stock VALUES (1, 10);
                CREATE TABLE test_document_series (id BIGINT PRIMARY KEY, next_number BIGINT);
                INSERT INTO test_document_series VALUES (1, 186);
                CREATE TABLE test_sale_effect (sale_id BIGINT REFERENCES sale(id), kind TEXT);
                """);
        applyMigration();
        UserRepository users = mock(UserRepository.class);
        when(users.findByUsername("seller")).thenReturn(Optional.of(new User(1L)));
        when(users.findByUsername("another-seller")).thenReturn(Optional.of(new User(2L)));
        createSale = mock(CreateSaleV2UseCase.class);
        doAnswer(invocation -> persistSaleEffects()).when(createSale).create(any(), anyString());
        var target = new CreateIdempotentSaleV2Service(createSale, users, repository,
                new ObjectMapper().registerModule(new JavaTimeModule()));
        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        service = (CreateIdempotentSaleV2Service) factory.getProxy();
    }

    @AfterEach
    void dropOnlyOwnedSchema() {
        if (schema != null && schema.matches("sale_idem_test_[a-f0-9]{32}")) {
            new JdbcTemplate(administrativeDataSource).execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    @Test
    void retryAfterLostResponseReturnsOriginalDocumentAndDoesNotRepeatAnyEffects() {
        SaleV2CreateRequest request = request();
        SaleV2DocumentResponse original = service.create(request, "seller");
        // Simulate changed conditions after commit (stock, session, converted proforma, permissions).
        doThrow(new InvalidSaleV2Exception("Ya no hay stock")).when(createSale).create(any(), anyString());
        SaleV2DocumentResponse replay = service.create(request, "seller");

        assertThat(replay).usingRecursiveComparison().isEqualTo(original);
        assertThat(executions).hasValue(1);
        assertExactlyOneSale();
        assertThat(number("SELECT COUNT(*) FROM sale_create_idempotency WHERE response_json IS NOT NULL"))
                .isEqualTo(1);
    }

    @Test
    @Timeout(15)
    void concurrentIdenticalRequestsWaitAndReturnTheSameSale() throws Exception {
        CountDownLatch firstSaved = new CountDownLatch(1);
        CountDownLatch allowCommit = new CountDownLatch(1);
        doAnswer(invocation -> {
            SaleV2DocumentResponse result = persistSaleEffects();
            firstSaved.countDown();
            assertThat(allowCommit.await(5, TimeUnit.SECONDS)).isTrue();
            return result;
        }).when(createSale).create(any(), anyString());
        SaleV2CreateRequest request = request();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> service.create(request, "seller"));
            assertThat(firstSaved.await(5, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> service.create(request, "seller"));
            try {
                assertThatThrownBy(() -> second.get(250, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            } finally {
                allowCommit.countDown();
            }
            assertThat(second.get(5, TimeUnit.SECONDS)).usingRecursiveComparison().isEqualTo(first.get(5, TimeUnit.SECONDS));
        }
        assertThat(executions).hasValue(1);
        assertExactlyOneSale();
    }

    @Test
    @Timeout(15)
    void concurrentChangedPayloadWaitsThenRejectsWithoutCreatingAnotherSale() throws Exception {
        CountDownLatch firstSaved = new CountDownLatch(1);
        CountDownLatch allowCommit = new CountDownLatch(1);
        doAnswer(invocation -> {
            SaleV2DocumentResponse result = persistSaleEffects();
            firstSaved.countDown();
            assertThat(allowCommit.await(5, TimeUnit.SECONDS)).isTrue();
            return result;
        }).when(createSale).create(any(), anyString());
        SaleV2CreateRequest request = request();
        SaleV2CreateRequest changed = request();
        changed.setIdempotencyKey(request.getIdempotencyKey());
        changed.setNotes("different sale");
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> service.create(request, "seller"));
            assertThat(firstSaved.await(5, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> service.create(changed, "seller"));
            try {
                assertThatThrownBy(() -> second.get(250, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            } finally {
                allowCommit.countDown();
            }
            first.get(5, TimeUnit.SECONDS);
            assertThatThrownBy(() -> second.get(5, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(SaleIdempotencyConflictException.class);
        }
        assertExactlyOneSale();
    }

    @Test
    void failedSaleRollsBackClaimStockDocumentNumberAndPaymentBeforeRetry() {
        SaleV2CreateRequest request = request();
        doAnswer(invocation -> {
            persistSaleEffects();
            throw new InvalidSaleV2Exception("Faltan seriales");
        }).when(createSale).create(any(), anyString());
        assertThatThrownBy(() -> service.create(request, "seller"))
                .isInstanceOf(SaleCreateRejectedException.class).hasMessage("Faltan seriales");
        assertThat(number("SELECT COUNT(*) FROM sale")).isZero();
        assertThat(number("SELECT COUNT(*) FROM test_sale_effect")).isZero();
        assertThat(number("SELECT COUNT(*) FROM sale_create_idempotency")).isZero();
        assertThat(number("SELECT quantity FROM test_stock WHERE id=1")).isEqualTo(10);
        assertThat(number("SELECT next_number FROM test_document_series WHERE id=1")).isEqualTo(186);

        doAnswer(invocation -> persistSaleEffects()).when(createSale).create(any(), anyString());
        assertThat(service.create(request, "seller").getNumber()).isEqualTo(186);
        assertExactlyOneSale();
    }

    @Test
    void sameKeyIsScopedToAuthenticatedUser() {
        SaleV2CreateRequest request = request();
        var first = service.create(request, "seller");
        var other = service.create(request, "another-seller");
        assertThat(other.getSaleId()).isNotEqualTo(first.getSaleId());
        assertThat(number("SELECT COUNT(*) FROM sale_create_idempotency")).isEqualTo(2);
        assertThat(number("SELECT COUNT(*) FROM sale")).isEqualTo(2);
    }

    @Test
    void missingMigrationIsFriendlyAndRejectsBeforeAnySaleEffect() {
        jdbc.sql("DROP TABLE sale_create_idempotency").update();
        assertThatThrownBy(() -> service.create(request(), "seller"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode().value()).isEqualTo(503);
                    assertThat(exception.getReason()).contains("falta completar una actualización");
                });
        assertThat(executions).hasValue(0);
        assertThat(number("SELECT COUNT(*) FROM sale")).isZero();
        assertThat(number("SELECT quantity FROM test_stock WHERE id=1")).isEqualTo(10);
    }

    @Test
    void migrationCanBeReappliedWithoutDiscardingCommittedResults() {
        SaleV2CreateRequest request = request();
        var sale = service.create(request, "seller");
        applyMigration();
        assertThat(service.create(request, "seller").getSaleId()).isEqualTo(sale.getSaleId());
        assertExactlyOneSale();
    }

    @Test
    void repositoryRefusesAutocommitClaimsThatCouldSurviveFailedSales() {
        assertThatThrownBy(() -> repository.acquire(1L, UUID.randomUUID(), "a".repeat(64)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("transacción");
        assertThat(number("SELECT COUNT(*) FROM sale_create_idempotency")).isZero();
    }

    @Test
    void failureStoringResponseRollsBackSaleEffectsAndClaim() {
        jdbc.sql("ALTER TABLE sale_create_idempotency ADD CONSTRAINT reject_response CHECK (sale_id IS NULL)").update();
        assertThatThrownBy(() -> service.create(request(), "seller"))
                .isNotInstanceOf(SaleCreateRejectedException.class);
        assertThat(number("SELECT COUNT(*) FROM sale_create_idempotency")).isZero();
        assertThat(number("SELECT COUNT(*) FROM sale")).isZero();
        assertThat(number("SELECT COUNT(*) FROM test_sale_effect")).isZero();
        assertThat(number("SELECT quantity FROM test_stock WHERE id=1")).isEqualTo(10);
        assertThat(number("SELECT next_number FROM test_document_series WHERE id=1")).isEqualTo(186);
    }

    private SaleV2DocumentResponse persistSaleEffects() {
        executions.incrementAndGet();
        jdbc.sql("UPDATE test_stock SET quantity=quantity-2 WHERE id=1").update();
        Long number = jdbc.sql("UPDATE test_document_series SET next_number=next_number+1 WHERE id=1 RETURNING next_number-1")
                .query(Long.class).single();
        Long saleId = jdbc.sql("INSERT INTO sale(total) VALUES (14.25) RETURNING id").query(Long.class).single();
        for (String effect : List.of("STOCK_MOVEMENT", "PAYMENT", "RESERVATION_REASSIGNMENT")) {
            jdbc.sql("INSERT INTO test_sale_effect VALUES (?, ?)").param(saleId).param(effect).update();
        }
        return SaleV2DocumentResponse.builder().saleId(saleId).docType(DocType.SIMPLE).series("S001").number(number)
                .issueDate(LocalDate.of(2026, 10, 22)).total(new BigDecimal("14.2500")).build();
    }

    private void assertExactlyOneSale() {
        assertThat(number("SELECT COUNT(*) FROM sale")).isEqualTo(1);
        assertThat(number("SELECT COUNT(*) FROM test_sale_effect")).isEqualTo(3);
        assertThat(number("SELECT quantity FROM test_stock WHERE id=1")).isEqualTo(8);
        assertThat(number("SELECT next_number FROM test_document_series WHERE id=1")).isEqualTo(187);
    }

    private long number(String sql) { return jdbc.sql(sql).query(Long.class).single(); }

    private void applyMigration() {
        new ResourceDatabasePopulator(new ClassPathResource("db/scripts/20261022_sale_create_idempotency.sql"))
                .execute(dataSource);
    }

    private static SaleV2CreateRequest request() {
        return SaleV2CreateRequest.builder().idempotencyKey(UUID.randomUUID().toString()).docType(DocType.SIMPLE)
                .items(List.of(SaleV2CreateRequest.Item.builder().productId(1L).quantity(new BigDecimal("2")).build()))
                .build();
    }
}
