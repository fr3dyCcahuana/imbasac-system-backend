package com.paulfernandosr.possystembackend.motorcycleavailability.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.motorcycleavailability.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PostgresMotorcycleAvailabilityRepository implements MotorcycleAvailabilityRepository {

    private final JdbcClient jdbcClient;

    @Override
    public MotorcycleAvailabilityPage findPage(MotorcycleAvailabilityQuery query) {
        int page = Math.max(query.getPage(), 0);
        int size = Math.min(Math.max(query.getSize(), 1), 60);

        SqlParts sqlParts = buildBaseSql(query, false);
        SqlParts sqlPartsWithStatus = buildBaseSql(query, true);

        MotorcycleAvailabilityStats stats = jdbcClient.sql(sqlParts.baseSql() + """
                SELECT
                  COUNT(*) FILTER (WHERE available_units > 0) AS available_models,
                  COALESCE(SUM(available_units), 0) AS available_units,
                  COALESCE(SUM(reserved_units), 0) AS reserved_units,
                  COUNT(*) FILTER (WHERE available_units <= 0) AS out_of_stock_models
                FROM base
                """)
                .params(sqlParts.params().toArray())
                .query((rs, rowNum) -> MotorcycleAvailabilityStats.builder()
                        .availableModels(rs.getLong("available_models"))
                        .availableUnits(rs.getLong("available_units"))
                        .reservedUnits(rs.getLong("reserved_units"))
                        .outOfStockModels(rs.getLong("out_of_stock_models"))
                        .build())
                .single();

        long totalElements = jdbcClient.sql(sqlPartsWithStatus.baseSql() + " SELECT COUNT(*) FROM base " + sqlPartsWithStatus.statusWhere())
                .params(sqlPartsWithStatus.params().toArray())
                .query(Long.class)
                .single();

        List<Object> pageParams = new ArrayList<>(sqlPartsWithStatus.params());
        pageParams.add(size);
        pageParams.add(page * size);

        List<MotorcycleAvailabilityItem> items = jdbcClient.sql(sqlPartsWithStatus.baseSql() + """
                SELECT
                  product_id,
                  sku,
                  name,
                  brand,
                  model,
                  engine_capacity,
                  warehouse_location,
                  main_image_url,
                  colors_csv,
                  available_units,
                  reserved_units,
                  cash_price,
                  initial_from,
                  includes_card_plate,
                  CASE
                    WHEN available_units > 0 THEN 'AVAILABLE'
                    WHEN reserved_units > 0 THEN 'RESERVED'
                    ELSE 'OUT_OF_STOCK'
                  END AS availability_status
                FROM base
                """ + sqlPartsWithStatus.statusWhere() + """
                ORDER BY display_order ASC, name ASC, sku ASC
                LIMIT ?
                OFFSET ?
                """)
                .params(pageParams.toArray())
                .query((rs, rowNum) -> MotorcycleAvailabilityItem.builder()
                        .productId(rs.getLong("product_id"))
                        .sku(rs.getString("sku"))
                        .name(rs.getString("name"))
                        .brand(rs.getString("brand"))
                        .model(rs.getString("model"))
                        .engineCapacity(rs.getString("engine_capacity"))
                        .warehouseLocation(rs.getString("warehouse_location"))
                        .mainImageUrl(rs.getString("main_image_url"))
                        .colors(splitColors(rs.getString("colors_csv")))
                        .availableUnits(rs.getLong("available_units"))
                        .reservedUnits(rs.getLong("reserved_units"))
                        .cashPrice(rs.getBigDecimal("cash_price"))
                        .initialFrom(rs.getBigDecimal("initial_from"))
                        .includesCardPlate(rs.getBoolean("includes_card_plate"))
                        .status(rs.getString("availability_status"))
                        .canCreateContract(rs.getLong("available_units") > 0)
                        .build())
                .list();

        int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) totalElements / size);

        return MotorcycleAvailabilityPage.builder()
                .response(MotorcycleAvailabilityResponse.builder()
                        .stats(stats)
                        .items(items)
                        .build())
                .page(page)
                .size(size)
                .numberOfElements(items.size())
                .totalElements(totalElements)
                .totalPages(totalPages)
                .build();
    }

    @Override
    public MotorcycleAvailabilityFilterOptions findFilterOptions() {
        String baseWhere = """
                FROM product p
                LEFT JOIN product_vehicle_specs vs ON vs.product_id = p.id
                LEFT JOIN motorcycle_availability_config cfg ON cfg.product_id = p.id
                WHERE UPPER(COALESCE(p.category, '')) = 'MOTOCICLETAS'
                  AND COALESCE(p.manage_by_serial, FALSE) = TRUE
                  AND COALESCE(cfg.published, TRUE) = TRUE
                """;

        List<String> brands = jdbcClient.sql("""
                SELECT DISTINCT TRIM(p.brand)
                """ + baseWhere + """
                  AND NULLIF(TRIM(COALESCE(p.brand, '')), '') IS NOT NULL
                ORDER BY TRIM(p.brand)
                """)
                .query(String.class)
                .list();

        List<String> engineCapacities = jdbcClient.sql("""
                SELECT DISTINCT TRIM(vs.engine_capacity)
                """ + baseWhere + """
                  AND NULLIF(TRIM(COALESCE(vs.engine_capacity, '')), '') IS NOT NULL
                ORDER BY TRIM(vs.engine_capacity)
                """)
                .query(String.class)
                .list();

        List<String> warehouseLocations = jdbcClient.sql("""
                SELECT DISTINCT TRIM(p.warehouse_location)
                """ + baseWhere + """
                  AND NULLIF(TRIM(COALESCE(p.warehouse_location, '')), '') IS NOT NULL
                ORDER BY TRIM(p.warehouse_location)
                """)
                .query(String.class)
                .list();

        return MotorcycleAvailabilityFilterOptions.builder()
                .brands(brands)
                .engineCapacities(engineCapacities)
                .warehouseLocations(warehouseLocations)
                .statuses(List.of("AVAILABLE", "RESERVED", "OUT_OF_STOCK"))
                .build();
    }

    @Override
    public List<MotorcycleAvailabilityUnit> findUnits(Long productId) {
        String sql = """
                SELECT
                  psu.id AS serial_unit_id,
                  psu.vin,
                  psu.chassis_number,
                  psu.engine_number,
                  psu.color,
                  psu.year_make,
                  psu.status,
                  psu.contract_id
                FROM product_serial_unit psu
                JOIN product p ON p.id = psu.product_id
                WHERE psu.product_id = ?
                  AND UPPER(COALESCE(p.category, '')) = 'MOTOCICLETAS'
                  AND COALESCE(p.manage_by_serial, FALSE) = TRUE
                ORDER BY
                  CASE psu.status
                    WHEN 'EN_ALMACEN' THEN 1
                    WHEN 'RESERVADO' THEN 2
                    ELSE 3
                  END,
                  psu.created_at ASC,
                  psu.id ASC
                """;

        return jdbcClient.sql(sql)
                .param(productId)
                .query((rs, rowNum) -> mapUnit(rs))
                .list();
    }

    @Override
    public Optional<MotorcycleAvailabilityUnit> findUnit(Long productId, Long serialUnitId) {
        String sql = """
                SELECT
                  psu.id AS serial_unit_id,
                  psu.vin,
                  psu.chassis_number,
                  psu.engine_number,
                  psu.color,
                  psu.year_make,
                  psu.status,
                  psu.contract_id
                FROM product_serial_unit psu
                JOIN product p ON p.id = psu.product_id
                WHERE psu.product_id = ?
                  AND psu.id = ?
                  AND UPPER(COALESCE(p.category, '')) = 'MOTOCICLETAS'
                  AND COALESCE(p.manage_by_serial, FALSE) = TRUE
                """;

        return jdbcClient.sql(sql)
                .params(productId, serialUnitId)
                .query((rs, rowNum) -> mapUnit(rs))
                .optional();
    }

    private MotorcycleAvailabilityUnit mapUnit(java.sql.ResultSet rs) throws java.sql.SQLException {
        String status = rs.getString("status");
        Long contractId = (Long) rs.getObject("contract_id");
        return MotorcycleAvailabilityUnit.builder()
                .serialUnitId(rs.getLong("serial_unit_id"))
                .vin(rs.getString("vin"))
                .chassisNumber(rs.getString("chassis_number"))
                .engineNumber(rs.getString("engine_number"))
                .color(rs.getString("color"))
                .yearMake((Integer) rs.getObject("year_make"))
                .status(status)
                .contractId(contractId)
                .canCreateContract("EN_ALMACEN".equalsIgnoreCase(status) && contractId == null)
                .build();
    }

    private SqlParts buildBaseSql(MotorcycleAvailabilityQuery query, boolean includeStatus) {
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder("""
                WHERE UPPER(COALESCE(p.category, '')) = 'MOTOCICLETAS'
                  AND COALESCE(p.manage_by_serial, FALSE) = TRUE
                  AND COALESCE(cfg.published, TRUE) = TRUE
                """);

        String q = clean(query.getQuery());
        if (!q.isBlank()) {
            String like = "%" + q + "%";
            where.append("""
                    AND (
                      p.sku ILIKE ?
                      OR p.name ILIKE ?
                      OR COALESCE(p.brand, '') ILIKE ?
                      OR COALESCE(p.model, '') ILIKE ?
                    )
                    """);
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }

        String brand = clean(query.getBrand());
        if (!brand.isBlank()) {
            where.append(" AND LOWER(TRIM(COALESCE(p.brand, ''))) = LOWER(TRIM(?))\n");
            params.add(brand);
        }

        String engineCapacity = clean(query.getEngineCapacity());
        if (!engineCapacity.isBlank()) {
            where.append(" AND LOWER(TRIM(COALESCE(vs.engine_capacity, ''))) = LOWER(TRIM(?))\n");
            params.add(engineCapacity);
        }

        String warehouseLocation = clean(query.getWarehouseLocation());
        if (!warehouseLocation.isBlank()) {
            where.append(" AND LOWER(TRIM(COALESCE(p.warehouse_location, ''))) = LOWER(TRIM(?))\n");
            params.add(warehouseLocation);
        }

        String statusWhere = "";
        if (includeStatus) {
            String status = clean(query.getStatus()).toUpperCase();
            statusWhere = switch (status) {
                case "AVAILABLE" -> " WHERE available_units > 0 ";
                case "RESERVED" -> " WHERE reserved_units > 0 ";
                case "OUT_OF_STOCK" -> " WHERE available_units <= 0 ";
                default -> "";
            };
        }

        String baseSql = """
                WITH base AS (
                  SELECT
                    p.id AS product_id,
                    p.sku,
                    p.name,
                    p.brand,
                    p.model,
                    vs.engine_capacity,
                    p.warehouse_location,
                    p.price_a AS cash_price,
                    cfg.initial_from,
                    COALESCE(cfg.includes_card_plate, TRUE) AS includes_card_plate,
                    COALESCE(cfg.display_order, 999999) AS display_order,
                    img.image_url AS main_image_url,
                    COALESCE(unit_agg.available_units, 0) AS available_units,
                    COALESCE(unit_agg.reserved_units, 0) AS reserved_units,
                    COALESCE(unit_agg.colors_csv, '') AS colors_csv
                  FROM product p
                  LEFT JOIN product_vehicle_specs vs ON vs.product_id = p.id
                  LEFT JOIN motorcycle_availability_config cfg ON cfg.product_id = p.id
                  LEFT JOIN LATERAL (
                    SELECT pi.image_url
                    FROM product_image pi
                    WHERE pi.product_id = p.id
                    ORDER BY pi.is_main DESC, pi.position ASC, pi.created_at ASC
                    LIMIT 1
                  ) img ON TRUE
                  LEFT JOIN LATERAL (
                    SELECT
                      COUNT(*) FILTER (WHERE psu.status = 'EN_ALMACEN') AS available_units,
                      COUNT(*) FILTER (WHERE psu.status = 'RESERVADO') AS reserved_units,
                      STRING_AGG(DISTINCT NULLIF(TRIM(psu.color), ''), '||')
                        FILTER (WHERE psu.status = 'EN_ALMACEN' AND NULLIF(TRIM(COALESCE(psu.color, '')), '') IS NOT NULL) AS colors_csv
                    FROM product_serial_unit psu
                    WHERE psu.product_id = p.id
                  ) unit_agg ON TRUE
                """ + where + """
                )
                """;

        return new SqlParts(baseSql, params, statusWhere);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static List<String> splitColors(String colorsCsv) {
        if (colorsCsv == null || colorsCsv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(colorsCsv.split("\\|\\|"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();
    }

    private record SqlParts(String baseSql, List<Object> params, String statusWhere) {
    }
}
