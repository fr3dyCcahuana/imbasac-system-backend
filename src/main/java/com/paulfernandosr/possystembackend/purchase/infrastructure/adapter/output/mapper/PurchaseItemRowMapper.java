package com.paulfernandosr.possystembackend.purchase.infrastructure.adapter.output.mapper;

import com.paulfernandosr.possystembackend.purchase.domain.PurchaseItem;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class PurchaseItemRowMapper implements RowMapper<PurchaseItem> {

    @Override
    public PurchaseItem mapRow(ResultSet rs, int rowNum) throws SQLException {
        return PurchaseItem.builder()
                .id(rs.getLong("purchase_item_id"))
                .purchaseId(rs.getLong("purchase_id"))
                .lineNumber(rs.getInt("line_number"))
                .productId(rs.getLong("product_id"))
                .description(rs.getString("description"))
                .presentation(rs.getString("presentation"))
                .quantity(rs.getBigDecimal("quantity"))
                .unitCost(rs.getBigDecimal("unit_cost"))
                .discountPercent(rs.getBigDecimal("discount_percent"))
                .discountAmount(rs.getBigDecimal("discount_amount"))
                .igvRate(rs.getBigDecimal("igv_rate"))
                .igvAmount(rs.getBigDecimal("igv_amount"))
                .freightAllocated(rs.getBigDecimal("freight_allocated"))
                .totalCost(rs.getBigDecimal("total_cost"))
                .lotCode(rs.getString("lot_code"))
                .expirationDate(rs.getDate("expiration_date") != null
                        ? rs.getDate("expiration_date").toLocalDate()
                        : null)
                // En el detalle no necesitamos exponer createdAt del ítem.
                .createdAt(null)
                .build();
    }
}
