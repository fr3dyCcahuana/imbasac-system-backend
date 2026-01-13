package com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.mapper;

import com.paulfernandosr.possystembackend.customer.domain.Customer;
import com.paulfernandosr.possystembackend.customer.domain.DocumentType;
import com.paulfernandosr.possystembackend.role.domain.Role;
import com.paulfernandosr.possystembackend.role.domain.RoleName;
import com.paulfernandosr.possystembackend.sale.domain.Sale;
import com.paulfernandosr.possystembackend.sale.domain.SaleStatus;
import com.paulfernandosr.possystembackend.sale.domain.SaleType;
import com.paulfernandosr.possystembackend.user.domain.User;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class SaleRowMapper implements RowMapper<Sale> {
    @Override
    public Sale mapRow(ResultSet rs, int rowNum) throws SQLException {
        return Sale.builder()
                .id(rs.getLong("sale_id"))
                .serial(SaleType.valueOf(rs.getString("type")).getSerial())
                .number(rs.getLong("number"))
                .type(SaleType.valueOf(rs.getString("type")))
                .discount(rs.getBigDecimal("discount"))
                .status(SaleStatus.valueOf(rs.getString("status")))
                .comment(rs.getString("comment"))
                .issuedAt(rs.getTimestamp("issued_at").toLocalDateTime())
                .customer(Customer.builder()
                        .id(rs.getLong("customer_id"))
                        .legalName(rs.getString("customer_name"))
                        .documentType(DocumentType.valueOf(rs.getString("customer_document_type")))
                        .documentNumber(rs.getString("customer_document_number"))
                        .address(rs.getString("customer_address"))
                        .enabled(rs.getBoolean("customer_enabled"))
                        .build())
                .issuedBy(User.builder()
                        .id(rs.getLong("user_id"))
                        .firstName(rs.getString("user_first_name"))
                        .lastName(rs.getString("user_last_name"))
                        .username(rs.getString("username"))
                        .role(Role.builder()
                                .id(rs.getLong("role_id"))
                                .name(RoleName.valueOf(rs.getString("role_name")))
                                .description(rs.getString("role_description"))
                                .build())
                        .build())
                .build();
    }
}
