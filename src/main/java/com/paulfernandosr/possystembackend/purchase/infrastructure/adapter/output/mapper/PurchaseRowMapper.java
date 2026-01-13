package com.paulfernandosr.possystembackend.purchase.infrastructure.adapter.output.mapper;

import com.paulfernandosr.possystembackend.purchase.domain.Purchase;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

public class PurchaseRowMapper implements RowMapper<Purchase> {

    @Override
    public Purchase mapRow(ResultSet rs, int rowNum) throws SQLException {
        return Purchase.builder()
                .id(rs.getLong("purchase_id"))
                .documentType(rs.getString("document_type"))
                .documentSeries(rs.getString("document_series"))
                .documentNumber(rs.getString("document_number"))
                .issueDate(rs.getDate("issue_date").toLocalDate())
                .entryDate(rs.getDate("entry_date").toLocalDate())
                .dueDate(rs.getDate("due_date") != null ? rs.getDate("due_date").toLocalDate() : null)
                .currency(rs.getString("currency"))
                .paymentType(rs.getString("payment_type"))
                .supplierRuc(rs.getString("supplier_ruc"))
                .supplierBusinessName(rs.getString("supplier_business_name"))
                .igvRate(rs.getBigDecimal("igv_rate"))
                .subtotal(rs.getBigDecimal("subtotal"))
                .igvAmount(rs.getBigDecimal("igv_amount"))
                .total(rs.getBigDecimal("total"))
                .status(rs.getString("status"))
                .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime())
                .build();
    }
}
