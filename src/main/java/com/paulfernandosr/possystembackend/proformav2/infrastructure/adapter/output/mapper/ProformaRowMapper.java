package com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.output.mapper;

import com.paulfernandosr.possystembackend.proformav2.domain.Proforma;
import com.paulfernandosr.possystembackend.proformav2.domain.model.ProformaStatus;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

public class ProformaRowMapper implements RowMapper<Proforma> {

    @Override
    public Proforma mapRow(ResultSet rs, int rowNum) throws SQLException {
        Proforma.ProformaBuilder builder = Proforma.builder()
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
                .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime());

        // ✅ columnas opcionales (cuando viene con JOIN users)
        Set<String> cols = columns(rs);
        if (cols.contains("cashier_username")) {
            builder.cashierUsername(rs.getString("cashier_username"));
        }
        if (cols.contains("cashier_first_name")) {
            builder.cashierFirstName(rs.getString("cashier_first_name"));
        }
        if (cols.contains("cashier_last_name")) {
            builder.cashierLastName(rs.getString("cashier_last_name"));
        }

        return builder.build();
    }

    private static Set<String> columns(ResultSet rs) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        Set<String> set = new HashSet<>();
        for (int i = 1; i <= md.getColumnCount(); i++) {
            set.add(md.getColumnLabel(i)); // usa alias (AS ...)
        }
        return set;
    }
}
