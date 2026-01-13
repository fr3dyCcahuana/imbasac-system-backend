package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.salev2.domain.port.output.ProductStockMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
@RequiredArgsConstructor
public class PostgresProductStockMovementRepository implements ProductStockMovementRepository {

    private final JdbcClient jdbcClient;

    @Override
    public void createOutSale(Long productId, BigDecimal quantityOut, Long saleItemId) {
        // Ajusta columnas si tu kardex tiene nombres distintos.
        String sql = """
            INSERT INTO product_stock_movement(
              product_id,
              movement_type,
              source_table,
              source_id,
              quantity_out,
              created_at
            ) VALUES (?, 'OUT_SALE', 'sale_item', ?, ?, NOW())
        """;

        jdbcClient.sql(sql)
                .params(productId, saleItemId, quantityOut)
                .update();
    }
}
