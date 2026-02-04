package com.paulfernandosr.possystembackend.product.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.product.domain.ProductVehicleSpecs;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductVehicleSpecsRepository;
import com.paulfernandosr.possystembackend.product.infrastructure.adapter.output.mapper.ProductVehicleSpecsRowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PostgresProductVehicleSpecsRepository implements ProductVehicleSpecsRepository {

    private final JdbcClient jdbcClient;

    @Override
    public Optional<ProductVehicleSpecs> findByProductId(Long productId) {
        String sql = """
                SELECT
                    product_id,
                    vehicle_type,
                    bodywork,
                    engine_capacity,
                    engine_power,
                    fuel,
                    rolling_form,
                    cylinders,
                    seats,
                    passengers,
                    axles,
                    wheels,
                    length,
                    width,
                    height,
                    vehicle_class,
                    net_weight,
                    payload,
                    gross_weight
                FROM product_vehicle_specs
                WHERE product_id = ?
                """;

        return jdbcClient.sql(sql)
                .param(productId)
                .query(new ProductVehicleSpecsRowMapper())
                .optional();
    }

    @Override
    public boolean existsByProductId(Long productId) {
        String sql = "SELECT COUNT(1) FROM product_vehicle_specs WHERE product_id = ?";
        Long count = jdbcClient.sql(sql)
                .param(productId)
                .query(Long.class)
                .single();
        return count != null && count > 0;
    }

    @Override
    public void create(ProductVehicleSpecs specs) {
        String sql = """
                INSERT INTO product_vehicle_specs(
                    product_id,
                    vehicle_type,
                    bodywork,
                    engine_capacity,
                    engine_power,
                    fuel,
                    rolling_form,
                    cylinders,
                    seats,
                    passengers,
                    axles,
                    wheels,
                    length,
                    width,
                    height,
                    vehicle_class,
                    net_weight,
                    payload,
                    gross_weight
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """;

        jdbcClient.sql(sql)
                .params(
                        specs.getProductId(),
                        specs.getVehicleType(),
                                                specs.getBodywork(),
                        specs.getEngineCapacity(),
                        specs.getEnginePower(),
                        specs.getFuel(),
                        specs.getRollingForm(),
                        specs.getCylinders(),
                        specs.getSeats(),
                        specs.getPassengers(),
                        specs.getAxles(),
                        specs.getWheels(),
                        specs.getLength(),
                        specs.getWidth(),
                        specs.getHeight(),
                        specs.getVehicleClass(),
                        specs.getNetWeight(),
                        specs.getPayload(),
                        specs.getGrossWeight()
                )
                .update();
    }

    @Override
    public void update(ProductVehicleSpecs specs) {
        String sql = """
                UPDATE product_vehicle_specs
                   SET vehicle_type    = ?,
                       bodywork        = ?,
                       engine_capacity = ?,
                       engine_power    = ?,
                       fuel            = ?,
                       rolling_form    = ?,
                       cylinders       = ?,
                       seats           = ?,
                       passengers      = ?,
                       axles           = ?,
                       wheels          = ?,
                       length          = ?,
                       width           = ?,
                       height          = ?,
                       vehicle_class   = ?,
                       net_weight      = ?,
                       payload         = ?,
                       gross_weight    = ?,
                       updated_at      = NOW()
                 WHERE product_id      = ?
                """;

        jdbcClient.sql(sql)
                .params(
                        specs.getVehicleType(),
                                                specs.getBodywork(),
                        specs.getEngineCapacity(),
                        specs.getEnginePower(),
                        specs.getFuel(),
                        specs.getRollingForm(),
                        specs.getCylinders(),
                        specs.getSeats(),
                        specs.getPassengers(),
                        specs.getAxles(),
                        specs.getWheels(),
                        specs.getLength(),
                        specs.getWidth(),
                        specs.getHeight(),
                        specs.getVehicleClass(),
                        specs.getNetWeight(),
                        specs.getPayload(),
                        specs.getGrossWeight(),
                        specs.getProductId()
                )
                .update();
    }
}
