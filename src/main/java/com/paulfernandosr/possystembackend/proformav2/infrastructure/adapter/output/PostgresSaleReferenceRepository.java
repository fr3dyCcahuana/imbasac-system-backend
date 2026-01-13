package com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.proformav2.domain.port.output.SaleReferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PostgresSaleReferenceRepository implements SaleReferenceRepository {

    private final JdbcClient jdbcClient;

    @Override
    public void create(Long saleId, Long proformaId) {
        String sql = """
            INSERT INTO sale_reference(sale_id, proforma_id, imported_at)
            VALUES (?,?, NOW())
            """;
        jdbcClient.sql(sql)
                .params(saleId, proformaId)
                .update();
    }
}
