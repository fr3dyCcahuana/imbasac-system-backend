package com.paulfernandosr.possystembackend.product.infrastructure.adapter.output.mapper;

import com.paulfernandosr.possystembackend.product.domain.Product;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ProductRowMapper implements RowMapper<Product> {

    @Override
    public Product mapRow(ResultSet rs, int rowNum) throws SQLException {
        return Product.builder()
                .id(rs.getLong("product_id"))
                .sku(rs.getString("sku"))
                .name(rs.getString("name"))
                .productType(rs.getString("product_type"))
                .category(rs.getString("category"))
                .presentation(rs.getString("presentation"))
                .factor(rs.getBigDecimal("factor"))

                // ✅ nuevos
                .manageBySerial(rs.getObject("manage_by_serial", Boolean.class))
                .facturableSunat(rs.getObject("facturable_sunat", Boolean.class))
                .affectsStock(rs.getObject("affects_stock", Boolean.class))
                .giftAllowed(rs.getObject("gift_allowed", Boolean.class))

                .originType(rs.getString("origin_type"))
                .originCountry(rs.getString("origin_country"))
                .factoryCode(rs.getString("factory_code"))
                .compatibility(rs.getString("compatibility"))
                .barcode(rs.getString("barcode"))
                .warehouseLocation(rs.getString("warehouse_location"))
                .priceA(rs.getBigDecimal("price_a"))
                .priceB(rs.getBigDecimal("price_b"))
                .priceC(rs.getBigDecimal("price_c"))
                .priceD(rs.getBigDecimal("price_d"))
                .costReference(rs.getBigDecimal("cost_reference"))
                .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime())
                .build();
    }
}
