package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.salev2.domain.port.output.SaleCreateIdempotencyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PostgresSaleCreateIdempotencyRepository implements SaleCreateIdempotencyRepository {
    private final JdbcClient jdbc;

    @Override
    public Entry acquire(Long userId, UUID key, String requestHash) {
        requireTransaction();
        if (!jdbc.sql("SELECT to_regclass('sale_create_idempotency') IS NOT NULL")
                .query(Boolean.class).single()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No se puede registrar la venta porque falta completar una actualización del sistema. "
                            + "Contacta al administrador y vuelve a intentar cuando esté aplicada.");
        }
        // ON CONFLICT waits for the competing transaction to commit or roll back. The next
        // statement sees its result under PostgreSQL's default READ COMMITTED isolation.
        jdbc.sql("""
                INSERT INTO sale_create_idempotency (user_id, idempotency_key, request_hash)
                VALUES (?, ?, ?) ON CONFLICT (user_id, idempotency_key) DO NOTHING
                """).param(userId).param(key).param(requestHash).update();
        return jdbc.sql("""
                SELECT request_hash, response_json::text FROM sale_create_idempotency
                 WHERE user_id = ? AND idempotency_key = ? FOR UPDATE
                """).param(userId).param(key)
                .query((rs, rowNum) -> new Entry(rs.getString("request_hash"), rs.getString("response_json")))
                .single();
    }

    @Override
    public void complete(Long userId, UUID key, Long saleId, String responseJson) {
        requireTransaction();
        int updated = jdbc.sql("""
                UPDATE sale_create_idempotency SET sale_id = ?, response_json = CAST(? AS jsonb), completed_at = NOW()
                 WHERE user_id = ? AND idempotency_key = ? AND response_json IS NULL
                """).param(saleId).param(responseJson).param(userId).param(key).update();
        if (updated != 1) {
            throw new IllegalStateException("No se pudo conservar el resultado del registro de venta.");
        }
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("La protección de reintentos requiere la transacción de la venta.");
        }
    }
}
