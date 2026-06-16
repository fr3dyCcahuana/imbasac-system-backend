package com.paulfernandosr.possystembackend.purchase.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.purchase.domain.PurchaseSerialUnit;
import com.paulfernandosr.possystembackend.purchase.domain.model.SerialIdentifierConflict;
import com.paulfernandosr.possystembackend.purchase.domain.port.output.ProductSerialUnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class PostgresPurchaseProductSerialUnitRepository implements ProductSerialUnitRepository {

    private final JdbcClient jdbcClient;

    @Override
    public List<SerialIdentifierConflict> findExistingIdentifiers(Set<String> vins,
                                                                  Set<String> engineNumbers,
                                                                  Set<String> chassisNumbers) {
        return findExistingIdentifiersExcluding(vins, engineNumbers, chassisNumbers, Set.of());
    }

    @Override
    public List<SerialIdentifierConflict> findExistingIdentifiersExcluding(Set<String> vins,
                                                                           Set<String> engineNumbers,
                                                                           Set<String> chassisNumbers,
                                                                           Set<Long> excludedSerialUnitIds) {
        Set<String> v = vins == null ? Set.of() : vins;
        Set<String> e = engineNumbers == null ? Set.of() : engineNumbers;
        Set<String> c = chassisNumbers == null ? Set.of() : chassisNumbers;
        Set<Long> excluded = excludedSerialUnitIds == null ? Set.of() : excludedSerialUnitIds;

        if (v.isEmpty() && e.isEmpty() && c.isEmpty()) return List.of();

        List<Object> params = new ArrayList<>();
        List<String> clauses = new ArrayList<>();

        if (!v.isEmpty()) {
            clauses.add("(vin IS NOT NULL AND vin IN (" + placeholders(v.size()) + "))");
            params.addAll(v);
        }
        if (!e.isEmpty()) {
            clauses.add("(engine_number IS NOT NULL AND engine_number IN (" + placeholders(e.size()) + "))");
            params.addAll(e);
        }
        if (!c.isEmpty()) {
            clauses.add("(chassis_number IS NOT NULL AND chassis_number IN (" + placeholders(c.size()) + "))");
            params.addAll(c);
        }

        String sql = """
            SELECT id, product_id, vin, engine_number, chassis_number
            FROM product_serial_unit
            WHERE (""" + String.join(" OR ", clauses) + ")";

        if (!excluded.isEmpty()) {
            sql += " AND id NOT IN (" + placeholders(excluded.size()) + ")";
            params.addAll(excluded);
        }

        return jdbcClient.sql(sql)
                .params(params.toArray())
                .query(rs -> {
                    List<SerialIdentifierConflict> out = new ArrayList<>();
                    while (rs.next()) {
                        out.add(SerialIdentifierConflict.builder()
                                .id(rs.getLong("id"))
                                .productId(rs.getLong("product_id"))
                                .vin(rs.getString("vin"))
                                .engineNumber(rs.getString("engine_number"))
                                .chassisNumber(rs.getString("chassis_number"))
                                .build());
                    }
                    return out;
                });
    }

    @Override
    public void insertInboundSerialUnits(Long purchaseItemId,
                                         Long productId,
                                         List<PurchaseSerialUnit> serialUnits) {
        insertSerialUnits(purchaseItemId, productId, serialUnits, "EN_ALMACEN");
    }

    @Override
    public void insertPendingInboundSerialUnits(Long purchaseItemId,
                                                Long productId,
                                                List<PurchaseSerialUnit> serialUnits) {
        insertSerialUnits(purchaseItemId, productId, serialUnits, "PENDIENTE_INGRESO");
    }

    private void insertSerialUnits(Long purchaseItemId,
                                   Long productId,
                                   List<PurchaseSerialUnit> serialUnits,
                                   String status) {
        if (serialUnits == null || serialUnits.isEmpty()) return;

        String sql = """
            INSERT INTO product_serial_unit(
              product_id,
              vin,
              chassis_number,
              engine_number,
              color,
              year_make,
              dua_number,
              dua_item,
              status,
              purchase_item_id,
              created_at,
              updated_at
            ) VALUES (?,?,?,?,?,?,?,?, ?, ?, NOW(), NOW())
            """;

        for (PurchaseSerialUnit u : serialUnits) {
            jdbcClient.sql(sql)
                    .params(
                            productId,
                            emptyToNull(u.getVin()),
                            emptyToNull(u.getChassisNumber()),
                            emptyToNull(u.getEngineNumber()),
                            emptyToNull(u.getColor()),
                            u.getYearMake(),
                            emptyToNull(u.getDuaNumber()),
                            u.getDuaItem(),
                            status,
                            purchaseItemId
                    )
                    .update();
        }
    }

    @Override
    public void markSerialUnitsByPurchaseItemAsInWarehouse(Long purchaseItemId) {
        String sql = """
            UPDATE product_serial_unit
               SET status = 'EN_ALMACEN',
                   updated_at = NOW()
             WHERE purchase_item_id = ?
               AND status = 'PENDIENTE_INGRESO'
            """;

        jdbcClient.sql(sql)
                .param(purchaseItemId)
                .update();
    }

    @Override
    public void updateInboundSerialUnit(PurchaseSerialUnit u) {
        String sql = """
            UPDATE product_serial_unit
               SET vin = ?,
                   chassis_number = ?,
                   engine_number = ?,
                   color = ?,
                   year_make = ?,
                   dua_number = ?,
                   dua_item = ?,
                   updated_at = NOW()
             WHERE id = ?
               AND purchase_item_id = ?
               AND status IN ('EN_ALMACEN', 'PENDIENTE_INGRESO')
               AND sale_item_id IS NULL
               AND contract_id IS NULL
               AND NOT EXISTS (
                    SELECT 1
                      FROM counter_sale_serial_unit cssu
                     WHERE cssu.serial_unit_id = product_serial_unit.id
               )
            """;

        int updated = jdbcClient.sql(sql)
                .params(
                        emptyToNull(u.getVin()),
                        emptyToNull(u.getChassisNumber()),
                        emptyToNull(u.getEngineNumber()),
                        emptyToNull(u.getColor()),
                        u.getYearMake(),
                        emptyToNull(u.getDuaNumber()),
                        u.getDuaItem(),
                        u.getId(),
                        u.getPurchaseItemId()
                )
                .update();

        if (updated == 0) {
            throw new IllegalStateException("No se pudo actualizar el serial. Puede estar vendido, reservado, en contrato o no pertenecer al ítem de compra.");
        }
    }

    @Override
    public int countBlockedSerialUnitsByPurchaseItemId(Long purchaseItemId) {
        String sql = """
            SELECT COUNT(1)
              FROM product_serial_unit psu
             WHERE psu.purchase_item_id = ?
               AND (
                    psu.status NOT IN ('EN_ALMACEN', 'PENDIENTE_INGRESO')
                    OR psu.sale_item_id IS NOT NULL
                    OR psu.contract_id IS NOT NULL
                    OR EXISTS (
                        SELECT 1
                          FROM counter_sale_serial_unit cssu
                         WHERE cssu.serial_unit_id = psu.id
                    )
               )
            """;

        Long count = jdbcClient.sql(sql)
                .param(purchaseItemId)
                .query(Long.class)
                .single();
        return count == null ? 0 : count.intValue();
    }

    @Override
    public int countBlockedSerialUnitsByPurchaseId(Long purchaseId) {
        String sql = """
            SELECT COUNT(1)
              FROM product_serial_unit psu
              JOIN purchase_item pi ON pi.id = psu.purchase_item_id
             WHERE pi.purchase_id = ?
               AND COALESCE(pi.status, 'ACTIVE') = 'ACTIVE'
               AND (
                    psu.status NOT IN ('EN_ALMACEN', 'PENDIENTE_INGRESO')
                    OR psu.sale_item_id IS NOT NULL
                    OR psu.contract_id IS NOT NULL
                    OR EXISTS (
                        SELECT 1
                          FROM counter_sale_serial_unit cssu
                         WHERE cssu.serial_unit_id = psu.id
                    )
               )
            """;

        Long count = jdbcClient.sql(sql)
                .param(purchaseId)
                .query(Long.class)
                .single();
        return count == null ? 0 : count.intValue();
    }

    @Override
    public void markSerialUnitsByPurchaseItemAsBaja(Long purchaseItemId) {
        String sql = """
            UPDATE product_serial_unit
               SET status = 'BAJA',
                   updated_at = NOW()
             WHERE purchase_item_id = ?
               AND status IN ('EN_ALMACEN', 'PENDIENTE_INGRESO')
               AND sale_item_id IS NULL
               AND contract_id IS NULL
               AND NOT EXISTS (
                    SELECT 1
                      FROM counter_sale_serial_unit cssu
                     WHERE cssu.serial_unit_id = product_serial_unit.id
               )
            """;

        jdbcClient.sql(sql)
                .param(purchaseItemId)
                .update();
    }

    private static String placeholders(int n) {
        return java.util.stream.IntStream.range(0, n)
                .mapToObj(i -> "?")
                .collect(Collectors.joining(","));
    }

    private static String emptyToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
