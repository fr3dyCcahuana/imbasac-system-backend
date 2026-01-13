package com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.output.mapper;

import com.paulfernandosr.possystembackend.proformav2.domain.Proforma;
import com.paulfernandosr.possystembackend.proformav2.domain.model.ProformaStatus;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ProformaRowMapper implements RowMapper<Proforma> {
    @Override
    public Proforma mapRow(ResultSet rs, int rowNum) throws SQLException {
        return Proforma.builder()
                .id(rs.getLong("id"))
                .stationId(rs.getLong("station_id"))
                .createdBy(rs.getLong("created_by"))
                .series(rs.getString("series"))
                .number(rs.getLong("number"))
                .issueDate(rs.getDate("issue_date").toLocalDate())
                .priceList(rs.getString("price_list").charAt(0))
                .currency(rs.getString("currency"))
                .customerId((Long) rs.getObject("customer_id"))
                .customerDocType(rs.getString("customer_doc_type"))
                .customerDocNumber(rs.getString("customer_doc_number"))
                .customerName(rs.getString("customer_name"))
                .customerAddress(rs.getString("customer_address"))
                .notes(rs.getString("notes"))
                .subtotal(rs.getBigDecimal("subtotal"))
                .discountTotal(rs.getBigDecimal("discount_total"))
                .total(rs.getBigDecimal("total"))
                .status(ProformaStatus.valueOf(rs.getString("status")))
                .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime())
                .build();
    }
}
