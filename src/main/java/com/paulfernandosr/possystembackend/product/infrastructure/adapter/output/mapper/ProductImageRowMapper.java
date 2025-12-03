package com.paulfernandosr.possystembackend.product.infrastructure.adapter.output.mapper;

import com.paulfernandosr.possystembackend.product.domain.ProductImage;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ProductImageRowMapper implements RowMapper<ProductImage> {

    @Override
    public ProductImage mapRow(ResultSet rs, int rowNum) throws SQLException {
        return ProductImage.builder()
                .id(rs.getLong("id"))
                .productId(rs.getLong("product_id"))
                .imageUrl(rs.getString("image_url"))
                .position(rs.getShort("position"))
                .isMain(rs.getBoolean("is_main"))
                .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                .build();
    }
}
