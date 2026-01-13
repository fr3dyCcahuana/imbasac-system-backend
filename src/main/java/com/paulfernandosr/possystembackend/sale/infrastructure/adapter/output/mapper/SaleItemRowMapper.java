package com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.mapper;

import com.paulfernandosr.possystembackend.sale.domain.SaleItem;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class SaleItemRowMapper implements RowMapper<SaleItem> {
    @Override
    public SaleItem mapRow(ResultSet rs, int rowNum) throws SQLException {
        return SaleItem.builder()
                .price(rs.getBigDecimal("sale_item_price"))
                .quantity(rs.getInt("sale_item_quantity"))
                .build();
    }
}
