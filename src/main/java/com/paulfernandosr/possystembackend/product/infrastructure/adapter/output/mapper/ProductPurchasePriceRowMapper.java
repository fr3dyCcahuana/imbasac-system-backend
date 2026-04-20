package com.paulfernandosr.possystembackend.product.infrastructure.adapter.output.mapper;

import com.paulfernandosr.possystembackend.product.domain.ProductPurchasePrice;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

public class ProductPurchasePriceRowMapper implements RowMapper<ProductPurchasePrice> {

    @Override
    public ProductPurchasePrice mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp createdAt = rs.getTimestamp("created_at");

        return ProductPurchasePrice.builder()
                .purchaseItemId(rs.getLong("purchase_item_id"))
                .purchaseId(rs.getLong("purchase_id"))
                .lineNumber(rs.getObject("line_number", Integer.class))
                .issueDate(rs.getObject("issue_date", java.time.LocalDate.class))
                .entryDate(rs.getObject("entry_date", java.time.LocalDate.class))
                .documentType(rs.getString("document_type"))
                .documentSeries(rs.getString("document_series"))
                .documentNumber(rs.getString("document_number"))
                .currency(rs.getString("currency"))
                .paymentType(rs.getString("payment_type"))
                .supplierRuc(rs.getString("supplier_ruc"))
                .supplierBusinessName(rs.getString("supplier_business_name"))
                .supplierAddress(rs.getString("supplier_address"))
                .quantity(rs.getBigDecimal("quantity"))
                .unitCost(rs.getBigDecimal("unit_cost"))
                .discountPercent(rs.getBigDecimal("discount_percent"))
                .discountAmount(rs.getBigDecimal("discount_amount"))
                .igvRate(rs.getBigDecimal("igv_rate"))
                .igvAmount(rs.getBigDecimal("igv_amount"))
                .freightAllocated(rs.getBigDecimal("freight_allocated"))
                .totalCost(rs.getBigDecimal("total_cost"))
                .lotCode(rs.getString("lot_code"))
                .expirationDate(rs.getObject("expiration_date", java.time.LocalDate.class))
                .createdAt(createdAt != null ? createdAt.toLocalDateTime() : null)
                .build();
    }
}
