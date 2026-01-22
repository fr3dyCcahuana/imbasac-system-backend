package com.paulfernandosr.possystembackend.product.infrastructure.adapter.output.mapper;

import com.paulfernandosr.possystembackend.product.domain.ProductStock;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

public class ProductStockRowMapper implements RowMapper<ProductStock> {

    @Override
    public ProductStock mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp last = rs.getTimestamp("last_movement_at");
        return ProductStock.builder()
                .productId(rs.getLong("product_id"))
                .quantityOnHand(rs.getBigDecimal("quantity_on_hand"))
                .averageCost(rs.getBigDecimal("average_cost"))
                .lastUnitCost(rs.getBigDecimal("last_unit_cost"))
                .lastMovementAt(last != null ? last.toLocalDateTime() : null)
                .build();
    }
}
