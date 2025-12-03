package com.paulfernandosr.possystembackend.product.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.product.domain.ProductVehicleDetail;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductVehicleDetailRepository;
import com.paulfernandosr.possystembackend.product.infrastructure.adapter.output.mapper.ProductVehicleDetailRowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PostgresProductVehicleDetailRepository implements ProductVehicleDetailRepository {

    private final JdbcClient jdbcClient;

    @Override
    public Optional<ProductVehicleDetail> findByProductId(Long productId) {
        String sql = """
                SELECT
                    product_id,
                    vin,
                    serial_number,
                    engine_number,
                    color,
                    year_make,
                    year_model,
                    vehicle_class
                FROM product_vehicle_detail
                WHERE product_id = ?
                """;

        return jdbcClient.sql(sql)
                .param(productId)
                .query(new ProductVehicleDetailRowMapper())
                .optional();
    }

    @Override
    public boolean existsByProductId(Long productId) {
        String sql = "SELECT COUNT(1) FROM product_vehicle_detail WHERE product_id = ?";
        Long count = jdbcClient.sql(sql)
                .param(productId)
                .query(Long.class)
                .single();
        return count != null && count > 0;
    }

    @Override
    public void create(ProductVehicleDetail detail) {
        String sql = """
                INSERT INTO product_vehicle_detail(
                    product_id,
                    vin,
                    serial_number,
                    engine_number,
                    color,
                    year_make,
                    year_model,
                    vehicle_class
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

        jdbcClient.sql(sql)
                .params(
                        detail.getProductId(),
                        detail.getVin(),
                        detail.getSerialNumber(),
                        detail.getEngineNumber(),
                        detail.getColor(),
                        detail.getYearMake(),
                        detail.getYearModel(),
                        detail.getVehicleClass()
                )
                .update();
    }

    @Override
    public void update(ProductVehicleDetail detail) {
        String sql = """
                UPDATE product_vehicle_detail
                   SET vin           = ?,
                       serial_number = ?,
                       engine_number = ?,
                       color         = ?,
                       year_make     = ?,
                       year_model    = ?,
                       vehicle_class = ?
                 WHERE product_id   = ?
                """;

        jdbcClient.sql(sql)
                .params(
                        detail.getVin(),
                        detail.getSerialNumber(),
                        detail.getEngineNumber(),
                        detail.getColor(),
                        detail.getYearMake(),
                        detail.getYearModel(),
                        detail.getVehicleClass(),
                        detail.getProductId()
                )
                .update();
    }
}
