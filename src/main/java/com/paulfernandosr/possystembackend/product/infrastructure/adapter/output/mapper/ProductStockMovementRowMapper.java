package com.paulfernandosr.possystembackend.product.infrastructure.adapter.output.mapper;

import com.paulfernandosr.possystembackend.product.domain.ProductStockMovement;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

public class ProductStockMovementRowMapper implements RowMapper<ProductStockMovement> {

    @Override
    public ProductStockMovement mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp createdAt = rs.getTimestamp("created_at");
        return ProductStockMovement.builder()
                .id(rs.getLong("id"))
                .productId(rs.getLong("product_id"))
                .movementType(rs.getString("movement_type"))
                .sourceTable(rs.getString("source_table"))
                .sourceId(rs.getObject("source_id", Long.class))
                .quantityIn(rs.getBigDecimal("quantity_in"))
                .quantityOut(rs.getBigDecimal("quantity_out"))
                .unitCost(rs.getBigDecimal("unit_cost"))
                .totalCost(rs.getBigDecimal("total_cost"))
                .balanceQty(rs.getBigDecimal("balance_qty"))
                .balanceCost(rs.getBigDecimal("balance_cost"))
                .createdAt(createdAt != null ? createdAt.toLocalDateTime() : null)
                .build();
    }
}
