package com.paulfernandosr.possystembackend.product.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.product.domain.ProductSerialUnit;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductStockValidationRepository;
import com.paulfernandosr.possystembackend.product.infrastructure.adapter.input.dto.ProductStockValidationDto;
import com.paulfernandosr.possystembackend.product.infrastructure.adapter.output.mapper.ProductSerialUnitRowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class PostgresProductStockValidationRepository implements ProductStockValidationRepository {

    private final JdbcClient jdbcClient;

    @Override
    public Collection<ProductStockValidationDto> validate(List<Long> ids, boolean includeSerialUnits, int serialLimit) {

        List<Long> cleanIds = (ids == null) ? List.of() :
                ids.stream().filter(Objects::nonNull).distinct().toList();

        if (cleanIds.isEmpty()) return List.of();

        String placeholders = cleanIds.stream().map(x -> "?").collect(Collectors.joining(","));

        // 1) Base info + stock_available (igual que tu repo sales-detail)
        String sql = """
            WITH base AS (
              SELECT
                p.id,
                p.affects_stock,
                p.gift_allowed,
                p.facturable_sunat,
                CASE
                  WHEN p.manage_by_serial = TRUE
                   AND UPPER(COALESCE(p.category, '')) IN ('MOTOR','MOTOCICLETAS')
                  THEN TRUE ELSE FALSE
                END AS manage_by_serial,
                CASE
                  WHEN (p.manage_by_serial = TRUE
                   AND UPPER(COALESCE(p.category, '')) IN ('MOTOR','MOTOCICLETAS')) THEN (
                    SELECT COUNT(1)
                      FROM product_serial_unit psu
                     WHERE psu.product_id = p.id
                       AND psu.status = 'EN_ALMACEN'
                  )
                  ELSE COALESCE(ps.quantity_on_hand, 0)
                END AS stock_available
              FROM product p
              LEFT JOIN product_stock ps ON ps.product_id = p.id
              WHERE p.id IN (""" + placeholders + """
              )
            )
            SELECT
              id,
              affects_stock,
              gift_allowed,
              facturable_sunat,
              manage_by_serial,
              stock_available
            FROM base
            """;

        List<ProductStockValidationDto> found = jdbcClient.sql(sql)
                .params(cleanIds.toArray())
                .query((rs, rowNum) -> {
                    Boolean affectsStock = (Boolean) rs.getObject("affects_stock");
                    BigDecimal stockAvailable = rs.getBigDecimal("stock_available");
                    if (stockAvailable == null) stockAvailable = BigDecimal.ZERO;

                    boolean inStock;
                    if (Boolean.FALSE.equals(affectsStock)) {
                        inStock = true; // no bloquea venta
                    } else {
                        inStock = stockAvailable.compareTo(BigDecimal.ZERO) > 0;
                    }

                    return ProductStockValidationDto.builder()
                            .id(rs.getLong("id"))
                            .exists(true)
                            .affectsStock(affectsStock)
                            .giftAllowed((Boolean) rs.getObject("gift_allowed"))
                            .facturableSunat((Boolean) rs.getObject("facturable_sunat"))
                            .manageBySerial((Boolean) rs.getObject("manage_by_serial"))
                            .stockAvailable(stockAvailable)
                            .inStock(inStock)
                            .availableSerialUnits(List.of())
                            .build();
                })
                .list();

        Map<Long, ProductStockValidationDto> byId = found.stream()
                .collect(Collectors.toMap(ProductStockValidationDto::getId, x -> x, (a, b) -> a, LinkedHashMap::new));

        // 2) Serial units (si aplica)
        if (includeSerialUnits) {
            List<Long> serialProductIds = found.stream()
                    .filter(x -> Boolean.TRUE.equals(x.getManageBySerial()))
                    .map(ProductStockValidationDto::getId)
                    .toList();

            if (!serialProductIds.isEmpty()) {
                String psuPlaceholders = serialProductIds.stream().map(x -> "?").collect(Collectors.joining(","));

                // Nota: limit global para no matar performance; si quieres limit por product_id, lo armamos con window function.
                String serialSql = """
                    SELECT
                      id AS serial_unit_id,
                      product_id,
                      purchase_item_id,
                      sale_item_id,
                      stock_adjustment_id,
                      vin,
                      chassis_number,
                      engine_number,
                      color,
                      year_make,
                      dua_number,
                      dua_item,
                      status,
                      created_at,
                      updated_at
                    FROM product_serial_unit
                    WHERE product_id IN (""" + psuPlaceholders + """
                      )
                      AND status = 'EN_ALMACEN'
                    ORDER BY product_id ASC, created_at ASC
                    LIMIT ?
                    """;

                List<Object> params = new ArrayList<>(serialProductIds);
                params.add(serialLimit);

                Map<Long, List<ProductSerialUnit>> serialsByProduct = jdbcClient.sql(serialSql)
                        .params(params.toArray())
                        .query(new ProductSerialUnitRowMapper())
                        .list()
                        .stream()
                        .collect(Collectors.groupingBy(ProductSerialUnit::getProductId, LinkedHashMap::new, Collectors.toList()));

                for (var e : serialsByProduct.entrySet()) {
                    ProductStockValidationDto dto = byId.get(e.getKey());
                    if (dto != null) dto.setAvailableSerialUnits(e.getValue());
                }
            }
        }

        // 3) Completar con ids no encontrados (exists=false)
        List<ProductStockValidationDto> result = new ArrayList<>();
        for (Long id : cleanIds) {
            ProductStockValidationDto dto = byId.get(id);
            if (dto != null) {
                result.add(dto);
            } else {
                result.add(ProductStockValidationDto.builder()
                        .id(id)
                        .exists(false)
                        .stockAvailable(BigDecimal.ZERO)
                        .inStock(false)
                        .availableSerialUnits(List.of())
                        .build());
            }
        }

        return result;
    }
}