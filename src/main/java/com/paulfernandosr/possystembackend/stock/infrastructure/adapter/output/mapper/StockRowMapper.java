package com.paulfernandosr.possystembackend.stock.infrastructure.adapter.output.mapper;

import com.paulfernandosr.possystembackend.stock.domain.Stock;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class StockRowMapper implements RowMapper<Stock> {

    @Override
    public Stock mapRow(ResultSet rs, int rowNum) throws SQLException {
        Stock stock = new Stock();
        stock.setProductId(rs.getLong("product_id"));
        stock.setQuantityOnHand(rs.getBigDecimal("quantity_on_hand"));
        stock.setAverageCost(rs.getBigDecimal("average_cost"));
        stock.setLastUnitCost(rs.getBigDecimal("last_unit_cost"));

        if (rs.getTimestamp("last_movement_at") != null) {
            stock.setLastMovementAt(rs.getTimestamp("last_movement_at").toLocalDateTime());
        }

        return stock;
    }
}
