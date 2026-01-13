/*package com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.mapper;

import com.paulfernandosr.possystembackend.category.domain.Category;
import com.paulfernandosr.possystembackend.product.domain.Product;
import com.paulfernandosr.possystembackend.sale.domain.SaleItem;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class FullSaleItemRowMapper implements RowMapper<SaleItem> {
    @Override
    public SaleItem mapRow(ResultSet rs, int rowNum) throws SQLException {
        return SaleItem.builder()
                .product(Product.builder()
                        .id(rs.getLong("product_id"))
                        .name(rs.getString("product_name"))
                        .description(rs.getString("product_description"))
                        .originCode(rs.getString("product_origin_code"))
                        .barcode(rs.getString("product_barcode"))
                        .category(Category.builder()
                                .id(rs.getLong("category_id"))
                                .name(rs.getString("category_name"))
                                .description(rs.getString("category_description"))
                                .build())
                        .build())
                .price(rs.getBigDecimal("sale_item_price"))
                .quantity(rs.getInt("sale_item_quantity"))
                .build();
    }
}*/
