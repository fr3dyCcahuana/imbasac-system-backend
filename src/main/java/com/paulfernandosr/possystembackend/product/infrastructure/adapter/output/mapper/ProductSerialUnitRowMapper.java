package com.paulfernandosr.possystembackend.product.infrastructure.adapter.output.mapper;

import com.paulfernandosr.possystembackend.product.domain.ProductSerialUnit;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ProductSerialUnitRowMapper implements RowMapper<ProductSerialUnit> {

    @Override
    public ProductSerialUnit mapRow(ResultSet rs, int rowNum) throws SQLException {
        return ProductSerialUnit.builder()
                .id(rs.getLong("id"))
                .productId(rs.getLong("product_id"))
                .vin(rs.getString("vin"))
                .chassisNumber(rs.getString("chassis_number"))
                .engineNumber(rs.getString("engine_number"))
                .color(rs.getString("color"))
                .yearMake((Short) rs.getObject("year_make"))
                .duaNumber(rs.getString("dua_number"))
                .duaItem((Integer) rs.getObject("dua_item"))
                .status(rs.getString("status"))
                .purchaseItemId((Long) rs.getObject("purchase_item_id"))
                .saleItemId((Long) rs.getObject("sale_item_id"))
                .stockAdjustmentId((Long) rs.getObject("stock_adjustment_id"))
                .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime())
                .build();
    }
}
