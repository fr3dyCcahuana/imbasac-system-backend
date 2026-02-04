package com.paulfernandosr.possystembackend.product.infrastructure.adapter.output.mapper;

import com.paulfernandosr.possystembackend.product.domain.ProductVehicleSpecs;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ProductVehicleSpecsRowMapper implements RowMapper<ProductVehicleSpecs> {

    @Override
    public ProductVehicleSpecs mapRow(ResultSet rs, int rowNum) throws SQLException {
        return ProductVehicleSpecs.builder()
                .productId(rs.getLong("product_id"))
                .vehicleType(rs.getString("vehicle_type"))

                // Comunes
                .bodywork(rs.getString("bodywork"))
                .engineCapacity(rs.getBigDecimal("engine_capacity"))
                .fuel(rs.getString("fuel"))
                .cylinders(rs.getObject("cylinders", Integer.class))
                .netWeight(rs.getBigDecimal("net_weight"))
                .payload(rs.getBigDecimal("payload"))
                .grossWeight(rs.getBigDecimal("gross_weight"))

                // Solo MOTOCICLETA
                .vehicleClass(rs.getString("vehicle_class"))
                .enginePower(rs.getBigDecimal("engine_power"))
                .rollingForm(rs.getString("rolling_form"))
                .seats(rs.getObject("seats", Integer.class))
                .passengers(rs.getObject("passengers", Integer.class))
                .axles(rs.getObject("axles", Integer.class))
                .wheels(rs.getObject("wheels", Integer.class))
                .length(rs.getBigDecimal("length"))
                .width(rs.getBigDecimal("width"))
                .height(rs.getBigDecimal("height"))
                .build();
    }
}
