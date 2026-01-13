package com.paulfernandosr.possystembackend.stock.infrastructure.adapter.output.mapper;

import com.paulfernandosr.possystembackend.stock.domain.StockMovement;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class StockMovementRowMapper implements RowMapper<StockMovement> {

    @Override
    public StockMovement mapRow(ResultSet rs, int rowNum) throws SQLException {
        StockMovement movement = new StockMovement();
        movement.setId(rs.getLong("id"));
        movement.setProductId(rs.getLong("product_id"));
        movement.setMovementType(rs.getString("movement_type"));
        movement.setSourceTable(rs.getString("source_table"));
        movement.setSourceId(rs.getLong("source_id"));
        movement.setQuantityIn(rs.getBigDecimal("quantity_in"));
        movement.setQuantityOut(rs.getBigDecimal("quantity_out"));
        movement.setUnitCost(rs.getBigDecimal("unit_cost"));
        movement.setTotalCost(rs.getBigDecimal("total_cost"));
        movement.setBalanceQty(rs.getBigDecimal("balance_qty"));
        movement.setBalanceCost(rs.getBigDecimal("balance_cost"));

        if (rs.getTimestamp("created_at") != null) {
            movement.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        }

        return movement;
    }
}
