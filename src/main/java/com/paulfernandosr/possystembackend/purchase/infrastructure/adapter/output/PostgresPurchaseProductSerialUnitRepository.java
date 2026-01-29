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
        Set<String> v = vins == null ? Set.of() : vins;
        Set<String> e = engineNumbers == null ? Set.of() : engineNumbers;
        Set<String> c = chassisNumbers == null ? Set.of() : chassisNumbers;

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
            WHERE """ + String.join(" OR ", clauses);

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
            ) VALUES (?,?,?,?,?,?,?,?, 'EN_ALMACEN',?, NOW(), NOW())
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
                            purchaseItemId
                    )
                    .update();
        }
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
