package com.paulfernandosr.possystembackend.product.infrastructure.adapter.output.mapper;

import com.paulfernandosr.possystembackend.product.domain.ProductKardexEntry;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

public class ProductKardexEntryRowMapper implements RowMapper<ProductKardexEntry> {

    @Override
    public ProductKardexEntry mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp movementDate = rs.getTimestamp("movement_date");
        Date sourceIssueDate = rs.getDate("source_issue_date");

        return ProductKardexEntry.builder()
                .id(rs.getLong("id"))
                .movementDate(movementDate != null ? movementDate.toLocalDateTime() : null)

                .productId(rs.getLong("product_id"))
                .sku(rs.getString("sku"))
                .productName(rs.getString("product_name"))
                .category(rs.getString("category"))
                .brand(rs.getString("brand"))
                .model(rs.getString("model"))
                .presentation(rs.getString("presentation"))
                .manageBySerial(rs.getObject("manage_by_serial", Boolean.class))

                .movementType(rs.getString("movement_type"))
                .movementLabel(rs.getString("movement_label"))
                .direction(rs.getString("direction"))

                .sourceTable(rs.getString("source_table"))
                .sourceId(rs.getObject("source_id", Long.class))
                .sourceDocumentType(rs.getString("source_document_type"))
                .sourceSeries(rs.getString("source_series"))
                .sourceNumber(rs.getString("source_number"))
                .sourceIssueDate(sourceIssueDate != null ? sourceIssueDate.toLocalDate() : null)
                .sourceStatus(rs.getString("source_status"))
                .sourceLineNumber(rs.getObject("source_line_number", Integer.class))

                .counterpartType(rs.getString("counterpart_type"))
                .counterpartDocumentNumber(rs.getString("counterpart_document_number"))
                .counterpartName(rs.getString("counterpart_name"))

                .quantityIn(rs.getBigDecimal("quantity_in"))
                .quantityOut(rs.getBigDecimal("quantity_out"))
                .movementQuantity(rs.getBigDecimal("movement_quantity"))

                .stockBefore(rs.getBigDecimal("stock_before"))
                .stockAfter(rs.getBigDecimal("stock_after"))

                .unitCost(rs.getBigDecimal("unit_cost"))
                .totalCost(rs.getBigDecimal("total_cost"))
                .averageCostAfter(rs.getBigDecimal("average_cost_after"))

                .sourceUnitPrice(rs.getBigDecimal("source_unit_price"))
                .sourceLineTotal(rs.getBigDecimal("source_line_total"))

                .adjustmentReason(rs.getString("adjustment_reason"))
                .note(rs.getString("note"))
                .build();
    }
}
