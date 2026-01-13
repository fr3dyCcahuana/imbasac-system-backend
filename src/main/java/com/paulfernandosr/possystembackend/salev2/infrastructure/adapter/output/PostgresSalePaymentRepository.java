package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.salev2.domain.port.output.SalePaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
@RequiredArgsConstructor
public class PostgresSalePaymentRepository implements SalePaymentRepository {

    private final JdbcClient jdbcClient;

    @Override
    public void insert(Long saleId, String method, BigDecimal amount) {
        String sql = "INSERT INTO sale_payment(sale_id, method, amount) VALUES (?, ?, ?)";
        jdbcClient.sql(sql)
                .params(saleId, method, amount)
                .update();
    }
}
