package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output.mapper;

import com.paulfernandosr.possystembackend.salev2.domain.model.ProductSnapshot;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ProductSnapshotRowMapper implements RowMapper<ProductSnapshot> {
    @Override
    public ProductSnapshot mapRow(ResultSet rs, int rowNum) throws SQLException {
        return ProductSnapshot.builder()
                .id(rs.getLong("product_id"))
                .sku(rs.getString("sku"))
                .name(rs.getString("name"))
                .presentation(rs.getString("presentation"))
                .factor(rs.getBigDecimal("factor"))
                .priceA(rs.getBigDecimal("price_a"))
                .priceB(rs.getBigDecimal("price_b"))
                .priceC(rs.getBigDecimal("price_c"))
                .priceD(rs.getBigDecimal("price_d"))
                .facturableSunat(rs.getBoolean("facturable_sunat"))
                .affectsStock(rs.getBoolean("affects_stock"))
                .giftAllowed(rs.getBoolean("gift_allowed"))
                .build();
    }
}
