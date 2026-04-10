package com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.countersale.domain.model.SerialUnit;
import com.paulfernandosr.possystembackend.countersale.domain.port.output.ProductSerialUnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class CounterSalePostgresProductSerialUnitRepository implements ProductSerialUnitRepository {

    private final JdbcClient jdbcClient;

    @Override
    public List<SerialUnit> lockByIds(List<Long> serialUnitIds) {
        if (serialUnitIds == null || serialUnitIds.isEmpty()) return List.of();
        String placeholders = serialUnitIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = String.format("""
            SELECT id,
                   product_id AS productId,
                   status,
                   vin,
                   chassis_number AS chassisNumber,
                   engine_number AS engineNumber,
                   color,
                   year_make AS yearMake,
                   contract_id AS contractId
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
    public List<SerialUnit> lockByCounterSaleItemIds(List<Long> counterSaleItemIds) {
        if (counterSaleItemIds == null || counterSaleItemIds.isEmpty()) return List.of();
        String placeholders = counterSaleItemIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = String.format("""
            SELECT psu.id,
                   psu.product_id AS productId,
                   psu.status,
                   psu.vin,
                   psu.chassis_number AS chassisNumber,
                   psu.engine_number AS engineNumber,
                   psu.color,
                   psu.year_make AS yearMake,
                   psu.contract_id AS contractId
              FROM counter_sale_serial_unit cssu
              JOIN product_serial_unit psu ON psu.id = cssu.serial_unit_id
             WHERE cssu.counter_sale_item_id IN (%s)
             FOR UPDATE OF psu
        """, placeholders);
        return jdbcClient.sql(sql)
                .params(counterSaleItemIds.toArray())
                .query(SerialUnit.class)
                .list();
    }

    @Override
    public void markAsSold(Long serialUnitId) {
        String sql = """
            UPDATE product_serial_unit
               SET status = 'VENDIDO',
                   updated_at = NOW()
             WHERE id = ?
        """;
        jdbcClient.sql(sql).param(serialUnitId).update();
    }

    @Override
    public void releaseAfterVoid(Long serialUnitId) {
        String sql = """
            UPDATE product_serial_unit
               SET status = CASE
                              WHEN contract_id IS NOT NULL THEN 'RESERVADO'
                              ELSE 'EN_ALMACEN'
                            END,
                   updated_at = NOW()
             WHERE id = ?
        """;
        jdbcClient.sql(sql).param(serialUnitId).update();
    }
}
