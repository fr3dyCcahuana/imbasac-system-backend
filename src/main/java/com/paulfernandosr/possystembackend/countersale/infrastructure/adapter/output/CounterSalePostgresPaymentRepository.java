package com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.countersale.domain.port.output.CounterSalePaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
@RequiredArgsConstructor
public class CounterSalePostgresPaymentRepository implements CounterSalePaymentRepository {

    private final JdbcClient jdbcClient;

    @Override
    public void insert(Long counterSaleId, String method, BigDecimal amount) {
        jdbcClient.sql("INSERT INTO counter_sale_payment(counter_sale_id, method, amount) VALUES (?, ?, ?)")
                .params(counterSaleId, method, amount)
                .update();
    }

    @Override
    public void deleteByCounterSaleId(Long counterSaleId) {
        jdbcClient.sql("DELETE FROM counter_sale_payment WHERE counter_sale_id = ?")
                .param(counterSaleId)
                .update();
    }
}
