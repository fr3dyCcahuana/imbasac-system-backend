package com.paulfernandosr.possystembackend.product.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.common.domain.Page;
import com.paulfernandosr.possystembackend.common.domain.Pageable;
import com.paulfernandosr.possystembackend.common.infrastructure.mapper.QueryMapper;
import com.paulfernandosr.possystembackend.product.domain.ProductSerialUnit;
import com.paulfernandosr.possystembackend.product.domain.exception.InvalidProductSerialUnitException;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductSerialUnitRepository;
import com.paulfernandosr.possystembackend.product.infrastructure.adapter.output.mapper.ProductSerialUnitRowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

@Repository("productPostgresProductSerialUnitRepository")
@RequiredArgsConstructor
public class PostgresProductSerialUnitRepository implements ProductSerialUnitRepository {

    private final JdbcClient jdbcClient;

    @Override
    public ProductSerialUnit create(ProductSerialUnit unit) {
        String sql = """
            INSERT INTO product_serial_unit(
                product_id,
                purchase_item_id,
                stock_adjustment_id,
                vin,
                chassis_number,
                engine_number,
                color,
                year_make,
                dua_number,
                dua_item,
                status
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING
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
            """;

        try {
            return jdbcClient.sql(sql)
                    .params(
                            unit.getProductId(),
                            unit.getPurchaseItemId(),
                            unit.getStockAdjustmentId(),
                            unit.getVin(),
                            unit.getChassisNumber(),
                            unit.getEngineNumber(),
                            unit.getColor(),
                            unit.getYearMake(),
                            unit.getDuaNumber(),
                            unit.getDuaItem(),
                            unit.getStatus()
                    )
                    .query(new ProductSerialUnitRowMapper())
                    .single();
        } catch (DataIntegrityViolationException ex) {
            String msg = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();

            if (msg != null && msg.contains("ux_product_serial_unit_vin")) {
                throw new InvalidProductSerialUnitException("El VIN ya existe.");
            }
            if (msg != null && (msg.contains("ux_product_serial_unit_chassis") || msg.contains("ux_product_serial_unit_chassis_number"))) {
                throw new InvalidProductSerialUnitException("El chasis/serie ya existe.");
            }
            if (msg != null && msg.contains("ux_product_serial_unit_engine_number")) {
                throw new InvalidProductSerialUnitException("El engineNumber ya existe.");
            }
            throw ex;
        }
    }

    @Override
    public Page<ProductSerialUnit> findPage(Long productId, String query, String status, Pageable pageable) {
        String q = query == null ? "" : query.trim();
        String st = status == null ? "" : status.trim();
        String like = QueryMapper.formatAsLikeParam(q);

        String countSql = """
            SELECT COUNT(1)
              FROM product_serial_unit u
             WHERE u.product_id = ?
               AND (? = '' OR u.vin ILIKE ? OR u.chassis_number ILIKE ? OR u.engine_number ILIKE ?)
               AND (? = '' OR u.status = ?)
            """;

        long totalElements = jdbcClient.sql(countSql)
                .params(
                        productId,
                        q, like, like, like,
                        st, st
                )
                .query(Long.class)
                .single();

        String selectSql = """
            SELECT
                u.id AS serial_unit_id,
                u.product_id,
                u.purchase_item_id,
                u.sale_item_id,
                u.stock_adjustment_id,
                u.vin,
                u.chassis_number,
                u.engine_number,
                u.color,
                u.year_make,
                u.dua_number,
                u.dua_item,
                u.status,
                u.created_at,
                u.updated_at
              FROM product_serial_unit u
             WHERE u.product_id = ?
               AND (? = '' OR u.vin ILIKE ? OR u.chassis_number ILIKE ? OR u.engine_number ILIKE ?)
               AND (? IS NULL OR ? = '' OR u.status = ?)
             ORDER BY u.created_at DESC
             LIMIT ?
            OFFSET ?
            """;

        int size = pageable.getSize();
        int number = pageable.getNumber();

        List<ProductSerialUnit> items = jdbcClient.sql(selectSql)
                .params(
                        productId,
                        q, like, like, like,
                        status, status, status,
                        size,
                        number * size
                )
                .query(new ProductSerialUnitRowMapper())
                .list();

        BigDecimal totalPages = BigDecimal.valueOf(totalElements)
                .divide(BigDecimal.valueOf(size), 0, RoundingMode.CEILING);

        return Page.<ProductSerialUnit>builder()
                .content(items)
                .number(number)
                .size(size)
                .numberOfElements(items.size())
                .totalPages(totalPages.intValue())
                .totalElements(totalElements)
                .build();
    }

    @Override
    public Optional<ProductSerialUnit> findAvailableById(Long productId, Long serialUnitId) {
        String sql = """
            SELECT
                u.id AS serial_unit_id,
                u.product_id,
                u.purchase_item_id,
                u.sale_item_id,
                u.stock_adjustment_id,
                u.vin,
                u.chassis_number,
                u.engine_number,
                u.color,
                u.year_make,
                u.dua_number,
                u.dua_item,
                u.status,
                u.created_at,
                u.updated_at
              FROM product_serial_unit u
             WHERE u.product_id = ?
               AND u.id = ?
               AND u.status = 'EN_ALMACEN'
            """;

        return jdbcClient.sql(sql)
                .params(productId, serialUnitId)
                .query(new ProductSerialUnitRowMapper())
                .optional();
    }

    @Override
    public Optional<ProductSerialUnit> findAvailableByVin(Long productId, String vin) {
        String sql = baseFindAvailableBy("vin");
        return jdbcClient.sql(sql)
                .params(productId, vin)
                .query(new ProductSerialUnitRowMapper())
                .optional();
    }

    @Override
    public Optional<ProductSerialUnit> findAvailableByEngineNumber(Long productId, String engineNumber) {
        String sql = baseFindAvailableBy("engine_number");
        return jdbcClient.sql(sql)
                .params(productId, engineNumber)
                .query(new ProductSerialUnitRowMapper())
                .optional();
    }

    @Override
    public Optional<ProductSerialUnit> findAvailableBySerialNumber(Long productId, String serialNumber) {
        // Compatibilidad: antes se llamaba serialNumber, ahora es chassisNumber.
        // Puede haber duplicados si no hay índice único; por eso detectamos ambigüedad.
        String sql = baseFindAvailableBy("chassis_number") + " LIMIT 2";
        List<ProductSerialUnit> list = jdbcClient.sql(sql)
                .params(productId, serialNumber)
                .query(new ProductSerialUnitRowMapper())
                .list();

        if (list.size() > 1) {
            throw new InvalidProductSerialUnitException("chassisNumber es ambiguo (existen múltiples unidades con ese valor). Use vin o engineNumber.");
        }
        return list.stream().findFirst();
    }


    @Override
    public Optional<ProductSerialUnit> lockById(Long serialUnitId) {
        String sql = """
            SELECT
                u.id AS serial_unit_id,
                u.product_id,
                u.purchase_item_id,
                u.sale_item_id,
                u.stock_adjustment_id,
                u.vin,
                u.chassis_number,
                u.engine_number,
                u.color,
                u.year_make,
                u.dua_number,
                u.dua_item,
                u.status,
                u.created_at,
                u.updated_at
              FROM product_serial_unit u
             WHERE u.id = ?
             FOR UPDATE
            """;

        return jdbcClient.sql(sql)
                .param(serialUnitId)
                .query(new ProductSerialUnitRowMapper())
                .optional();
    }

    @Override
    public ProductSerialUnit updateCorrection(ProductSerialUnit unit) {
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
             RETURNING
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
            """;

        try {
            return jdbcClient.sql(sql)
                    .params(
                            unit.getVin(),
                            unit.getChassisNumber(),
                            unit.getEngineNumber(),
                            unit.getColor(),
                            unit.getYearMake(),
                            unit.getDuaNumber(),
                            unit.getDuaItem(),
                            unit.getId()
                    )
                    .query(new ProductSerialUnitRowMapper())
                    .single();
        } catch (DataIntegrityViolationException ex) {
            String msg = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();

            if (msg != null && msg.contains("ux_product_serial_unit_vin")) {
                throw new InvalidProductSerialUnitException("El VIN ya existe.");
            }
            if (msg != null && (msg.contains("ux_product_serial_unit_chassis") || msg.contains("ux_product_serial_unit_chassis_number"))) {
                throw new InvalidProductSerialUnitException("El chasis/serie ya existe.");
            }
            if (msg != null && msg.contains("ux_product_serial_unit_engine_number")) {
                throw new InvalidProductSerialUnitException("El engineNumber ya existe.");
            }
            throw ex;
        }
    }

    @Override
    public boolean existsCounterSaleLink(Long serialUnitId) {
        String sql = """
            SELECT EXISTS (
                SELECT 1
                  FROM counter_sale_serial_unit cssu
                 WHERE cssu.serial_unit_id = ?
            )
            """;

        Boolean exists = jdbcClient.sql(sql)
                .param(serialUnitId)
                .query(Boolean.class)
                .single();
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public Optional<String> findContractCorrectionBlockReason(Long serialUnitId) {
        String sql = """
            SELECT
                CASE
                    WHEN psu.contract_id IS NULL THEN NULL
                    WHEN c.id IS NULL THEN 'La unidad serial tiene un contrato asociado inválido.'
                    WHEN c.sale_id IS NOT NULL THEN 'No se puede corregir la unidad serial porque el contrato ya tiene venta asociada.'
                    WHEN c.status NOT IN ('PENDIENTE', 'CONFIRMADO') THEN 'No se puede corregir la unidad serial porque el contrato no está PENDIENTE o CONFIRMADO.'
                    ELSE NULL
                END AS reason
              FROM product_serial_unit psu
              LEFT JOIN contract c ON c.id = psu.contract_id
             WHERE psu.id = ?
            """;

        String reason = jdbcClient.sql(sql)
                .param(serialUnitId)
                .query(String.class)
                .optional()
                .orElse(null);

        if (reason == null || reason.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(reason);
    }

    @Override
    public void markAsBaja(Long serialUnitId, Long stockAdjustmentId) {
        String sql = """
            UPDATE product_serial_unit
               SET status = 'BAJA',
                   stock_adjustment_id = ?,
                   updated_at = NOW()
             WHERE id = ?
            """;

        jdbcClient.sql(sql)
                .params(stockAdjustmentId, serialUnitId)
                .update();
    }

    private String baseFindAvailableBy(String column) {
        return """
            SELECT
                u.id AS serial_unit_id,
                u.product_id,
                u.purchase_item_id,
                u.sale_item_id,
                u.stock_adjustment_id,
                u.vin,
                u.chassis_number,
                u.engine_number,
                u.color,
                u.year_make,
                u.dua_number,
                u.dua_item,
                u.status,
                u.created_at,
                u.updated_at
              FROM product_serial_unit u
             WHERE u.product_id = ?
               AND u.status = 'EN_ALMACEN'
               AND u.""" + column +
             """ 
             = ?
             LIMIT 1
            """;
    }
}
