package com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.output.mapper;

import com.paulfernandosr.possystembackend.proformav2.domain.ProformaItem;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ProformaItemRowMapper implements RowMapper<ProformaItem> {
    @Override
    public ProformaItem mapRow(ResultSet rs, int rowNum) throws SQLException {
        return ProformaItem.builder()
                .id(rs.getLong("id"))
                .proformaId(rs.getLong("proforma_id"))
                .lineNumber(rs.getInt("line_number"))
                .productId(rs.getLong("product_id"))
                .sku(rs.getString("sku"))
                .description(rs.getString("description"))
                .presentation(rs.getString("presentation"))
                .factor(rs.getBigDecimal("factor"))
                .quantity(rs.getBigDecimal("quantity"))
                .unitPrice(rs.getBigDecimal("unit_price"))
                .discountPercent(rs.getBigDecimal("discount_percent"))
                .discountAmount(rs.getBigDecimal("discount_amount"))
                .lineSubtotal(rs.getBigDecimal("line_subtotal"))
                .facturableSunat(rs.getBoolean("facturable_sunat"))
                .affectsStock(rs.getBoolean("affects_stock"))
                .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                .build();
    }
}
