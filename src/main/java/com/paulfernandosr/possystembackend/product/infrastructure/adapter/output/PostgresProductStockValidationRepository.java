package com.paulfernandosr.possystembackend.product.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.product.domain.ProductSerialUnit;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductStockValidationRepository;
import com.paulfernandosr.possystembackend.product.infrastructure.adapter.input.dto.ProductStockValidationDto;
import com.paulfernandosr.possystembackend.product.infrastructure.adapter.input.dto.ProductStockReservationDto;
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
    public Collection<ProductStockValidationDto> validate(List<Long> ids, boolean includeSerialUnits, int serialLimit, Long sourceProformaNumber) {

        List<Long> cleanIds = (ids == null) ? List.of() :
                ids.stream().filter(Objects::nonNull).distinct().toList();

        if (cleanIds.isEmpty()) return List.of();

        String placeholders = cleanIds.stream().map(x -> "?").collect(Collectors.joining(","));

        String sql = ProductStockAvailabilitySql.CTE + """
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
            """ + ProductStockAvailabilitySql.STOCK_COLUMNS + """
              FROM product p
            """ + ProductStockAvailabilitySql.JOINS + """
              WHERE p.id IN (""" + placeholders + """
              )
            )
            SELECT
              id,
              affects_stock,
              gift_allowed,
              facturable_sunat,
              manage_by_serial,
              stock_available,
              stock_on_hand,
              stock_reserved,
              stock_reserved_by_source
            FROM base
            """;

        List<ProductStockValidationDto> found = jdbcClient.sql(sql)
                .params(validationParams(sourceProformaNumber, cleanIds).toArray())
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
                            .stockOnHand(rs.getBigDecimal("stock_on_hand"))
                            .stockReserved(rs.getBigDecimal("stock_reserved"))
                            .stockReservedBySource(rs.getBigDecimal("stock_reserved_by_source"))
                            .stockReservations(List.of())
                            .inStock(inStock)
                            .availableSerialUnits(List.of())
                            .build();
                })
                .list();

        Map<Long, ProductStockValidationDto> byId = found.stream()
                .collect(Collectors.toMap(ProductStockValidationDto::getId, x -> x, (a, b) -> a, LinkedHashMap::new));

        attachOtherReservations(byId, sourceProformaNumber);

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
                        .stockOnHand(BigDecimal.ZERO)
                        .stockReserved(BigDecimal.ZERO)
                        .stockReservedBySource(BigDecimal.ZERO)
                        .stockReservations(List.of())
                        .inStock(false)
                        .availableSerialUnits(List.of())
                        .build());
            }
        }

        return result;
    }

    private List<Object> validationParams(Long sourceProformaNumber, List<Long> cleanIds) {
        List<Object> params = new ArrayList<>();
        params.add(sourceProformaNumber);
        params.addAll(cleanIds);
        return params;
    }

    private void attachOtherReservations(Map<Long, ProductStockValidationDto> byId, Long sourceProformaNumber) {
        List<Long> ids = byId.values().stream()
                .filter(product -> !Boolean.FALSE.equals(product.getAffectsStock()))
                .filter(product -> !Boolean.TRUE.equals(product.getManageBySerial()))
                .filter(product -> product.getStockReserved().signum() > 0)
                .map(ProductStockValidationDto::getId)
                .toList();
        if (ids.isEmpty()) return;

        String placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = """
            SELECT r.product_id, r.id AS reservation_id, r.proforma_id,
                   pf.series AS proforma_series, pf.number AS proforma_number,
                   r.reserved_by,
                   COALESCE(NULLIF(TRIM(CONCAT_WS(' ', u.first_name, u.last_name)), ''),
                            u.username, 'Sin vendedor') AS reserved_by_name,
                   r.quantity, r.expires_at
              FROM product_stock_reservation r
              JOIN proforma pf ON pf.id = r.proforma_id
              LEFT JOIN users u ON u.id = r.reserved_by
             WHERE r.status = 'ACTIVE'
               AND r.reserved_date = CURRENT_DATE
               AND r.proforma_id IS DISTINCT FROM (
                 SELECT id FROM proforma WHERE number = CAST(? AS BIGINT)
                  ORDER BY id DESC LIMIT 1
               )
               AND r.product_id IN (
            """ + placeholders + """
               )
             ORDER BY r.product_id, r.expires_at, r.created_at, r.id
            """;

        jdbcClient.sql(sql)
                .params(validationParams(sourceProformaNumber, ids).toArray())
                .query((rs, rowNum) -> new ReservationRow(
                        rs.getLong("product_id"),
                        ProductStockReservationDto.builder()
                                .reservationId(rs.getLong("reservation_id"))
                                .proformaId(rs.getLong("proforma_id"))
                                .proformaSeries(rs.getString("proforma_series"))
                                .proformaNumber(rs.getLong("proforma_number"))
                                .reservedBy((Long) rs.getObject("reserved_by"))
                                .reservedByName(rs.getString("reserved_by_name"))
                                .quantity(rs.getBigDecimal("quantity"))
                                .expiresAt(rs.getTimestamp("expires_at") == null ? null :
                                        rs.getTimestamp("expires_at").toLocalDateTime())
                                .build()
                ))
                .list()
                .stream()
                .collect(Collectors.groupingBy(ReservationRow::productId,
                        Collectors.mapping(ReservationRow::reservation, Collectors.toList())))
                .forEach((id, reservations) -> byId.get(id).setStockReservations(reservations));
    }

    private record ReservationRow(Long productId, ProductStockReservationDto reservation) {
    }
}
