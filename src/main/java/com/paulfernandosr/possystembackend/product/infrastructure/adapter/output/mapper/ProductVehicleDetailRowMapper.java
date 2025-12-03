package com.paulfernandosr.possystembackend.product.infrastructure.adapter.output.mapper;

import com.paulfernandosr.possystembackend.product.domain.ProductVehicleDetail;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ProductVehicleDetailRowMapper implements RowMapper<ProductVehicleDetail> {

    @Override
    public ProductVehicleDetail mapRow(ResultSet rs, int rowNum) throws SQLException {
        return ProductVehicleDetail.builder()
                .productId(rs.getLong("product_id"))
                .vin(rs.getString("vin"))
                .serialNumber(rs.getString("serial_number"))
                .engineNumber(rs.getString("engine_number"))
                .color(rs.getString("color"))
                .yearMake(rs.getObject("year_make") == null ? null : rs.getShort("year_make"))
                .yearModel(rs.getObject("year_model") == null ? null : rs.getShort("year_model"))
                .vehicleClass(rs.getString("vehicle_class"))
                .build();
    }
}
