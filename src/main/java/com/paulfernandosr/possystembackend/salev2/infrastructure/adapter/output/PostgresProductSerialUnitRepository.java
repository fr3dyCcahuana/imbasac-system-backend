package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.salev2.domain.model.SerialUnit;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.ProductSerialUnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class PostgresProductSerialUnitRepository implements ProductSerialUnitRepository {

    private final JdbcClient jdbcClient;

    @Override
    public List<SerialUnit> lockByIds(List<Long> serialUnitIds) {
        if (serialUnitIds == null || serialUnitIds.isEmpty()) return List.of();

        String placeholders = serialUnitIds.stream()
                .map(id -> "?")
                .collect(Collectors.joining(","));

        String sql = String.format("""
        SELECT id,
               product_id AS productId,
               status
          FROM product_serial_unit
         WHERE id IN (%s)
         FOR UPDATE
        """, placeholders);

        return jdbcClient.sql(sql)
                .params(serialUnitIds.toArray())
                .query(SerialUnit.class)
                .list();
    }

    @Override
    public void markAsSold(Long serialUnitId, Long saleItemId) {
        // Si tu tabla no tiene updated_at, elimina esa línea.
        String sql = """
            UPDATE product_serial_unit
               SET status = 'VENDIDO',
                   sale_item_id = ?,
                   updated_at = NOW()
             WHERE id = ?
        """;
        jdbcClient.sql(sql)
                .params(saleItemId, serialUnitId)
                .update();
    }
}
