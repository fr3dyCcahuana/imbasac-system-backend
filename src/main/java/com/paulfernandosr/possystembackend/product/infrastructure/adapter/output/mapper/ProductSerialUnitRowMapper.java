package com.paulfernandosr.possystembackend.product.infrastructure.adapter.output.mapper;

import com.paulfernandosr.possystembackend.product.domain.ProductSerialUnit;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

public class ProductSerialUnitRowMapper implements RowMapper<ProductSerialUnit> {

    @Override
    public ProductSerialUnit mapRow(ResultSet rs, int rowNum) throws SQLException {

        Integer yearMake = rs.getObject("year_make", Integer.class);
        Integer yearModel = rs.getObject("year_model", Integer.class);

        Timestamp createdAt = rs.getTimestamp("created_at");
        Timestamp updatedAt = rs.getTimestamp("updated_at");

        return ProductSerialUnit.builder()
                .id(rs.getLong("serial_unit_id"))
                .productId(rs.getLong("product_id"))
                .purchaseItemId(rs.getObject("purchase_item_id", Long.class))
                .saleItemId(rs.getObject("sale_item_id", Long.class))
                .vin(rs.getString("vin"))
                .serialNumber(rs.getString("serial_number"))
                .engineNumber(rs.getString("engine_number"))
                .color(rs.getString("color"))
                .yearMake(yearMake != null ? yearMake.shortValue() : null)
                .yearModel(yearModel != null ? yearModel.shortValue() : null)
                .vehicleClass(rs.getString("vehicle_class"))
                .status(rs.getString("status"))
                .locationCode(rs.getString("location_code"))
                .createdAt(createdAt != null ? createdAt.toLocalDateTime() : null)
                .updatedAt(updatedAt != null ? updatedAt.toLocalDateTime() : null)
                .build();
    }
}
