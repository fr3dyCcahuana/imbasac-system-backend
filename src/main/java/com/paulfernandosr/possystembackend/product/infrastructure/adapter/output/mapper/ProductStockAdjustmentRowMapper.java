package com.paulfernandosr.possystembackend.product.infrastructure.adapter.output.mapper;

import com.paulfernandosr.possystembackend.product.domain.ProductStockAdjustment;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

public class ProductStockAdjustmentRowMapper implements RowMapper<ProductStockAdjustment> {

    @Override
    public ProductStockAdjustment mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp createdAt = rs.getTimestamp("created_at");
        return ProductStockAdjustment.builder()
                .id(rs.getLong("id"))
                .productId(rs.getLong("product_id"))
                .movementType(rs.getString("movement_type"))
                .quantity(rs.getBigDecimal("quantity"))
                .unitCost(rs.getBigDecimal("unit_cost"))
                .totalCost(rs.getBigDecimal("total_cost"))
                .reason(rs.getString("reason"))
                .note(rs.getString("note"))
                .createdBy(rs.getObject("created_by", Long.class))
                .createdAt(createdAt != null ? createdAt.toLocalDateTime() : null)
                .build();
    }
}
