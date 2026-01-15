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
                vin,
                serial_number,
                engine_number,
                color,
                year_make,
                year_model,
                vehicle_class,
                status,
                location_code
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING
                id AS serial_unit_id,
                product_id,
                purchase_item_id,
                sale_item_id,
                vin,
                serial_number,
                engine_number,
                color,
                year_make,
                year_model,
                vehicle_class,
                status,
                location_code,
                created_at,
                updated_at
            """;

        try {
            return jdbcClient.sql(sql)
                    .params(
                            unit.getProductId(),
                            unit.getPurchaseItemId(),
                            unit.getVin(),
                            unit.getSerialNumber(),
                            unit.getEngineNumber(),
                            unit.getColor(),
                            unit.getYearMake(),
                            unit.getYearModel(),
                            unit.getVehicleClass(),
                            unit.getStatus(),
                            unit.getLocationCode()
                    )
                    .query(new ProductSerialUnitRowMapper())
                    .single();
        } catch (DataIntegrityViolationException ex) {
            String msg = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();

            if (msg != null && msg.contains("ux_product_serial_unit_vin")) {
                throw new InvalidProductSerialUnitException("El VIN ya existe.");
            }
            if (msg != null && msg.contains("ux_product_serial_unit_engine_number")) {
                throw new InvalidProductSerialUnitException("El engineNumber ya existe.");
            }
            throw ex;
        }
    }

    @Override
    public Page<ProductSerialUnit> findPage(Long productId, String query, String status, Pageable pageable) {
        String q = query == null ? "" : query;
        String like = QueryMapper.formatAsLikeParam(q);

        String countSql = """
            SELECT COUNT(1)
              FROM product_serial_unit u
             WHERE u.product_id = ?
               AND (? = '' OR u.vin ILIKE ? OR u.serial_number ILIKE ? OR u.engine_number ILIKE ?)
               AND (? IS NULL OR ? = '' OR u.status = ?)
            """;

        long totalElements = jdbcClient.sql(countSql)
                .params(
                        productId,
                        q, like, like, like,
                        status, status, status
                )
                .query(Long.class)
                .single();

        String selectSql = """
            SELECT
                u.id AS serial_unit_id,
                u.product_id,
                u.purchase_item_id,
                u.sale_item_id,
                u.vin,
                u.serial_number,
                u.engine_number,
                u.color,
                u.year_make,
                u.year_model,
                u.vehicle_class,
                u.status,
                u.location_code,
                u.created_at,
                u.updated_at
              FROM product_serial_unit u
             WHERE u.product_id = ?
               AND (? = '' OR u.vin ILIKE ? OR u.serial_number ILIKE ? OR u.engine_number ILIKE ?)
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
}
