package com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.mapper;

import com.paulfernandosr.possystembackend.common.infrastructure.mapper.QueryMapper;
import com.paulfernandosr.possystembackend.role.domain.Role;
import com.paulfernandosr.possystembackend.role.domain.RoleName;
import com.paulfernandosr.possystembackend.sale.domain.SaleSession;
import com.paulfernandosr.possystembackend.station.domain.Station;
import com.paulfernandosr.possystembackend.user.domain.User;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class SaleSessionRowMapper implements RowMapper<SaleSession> {
    @Override
    public SaleSession mapRow(ResultSet rs, int rowNum) throws SQLException {
        return SaleSession.builder()
                .id(rs.getLong("session_id"))
                .user(User.builder()
                        .id(rs.getLong("user_id"))
                        .firstName(rs.getString("first_name"))
                        .lastName(rs.getString("last_name"))
                        .username(rs.getString("username"))
                        .enabled(rs.getBoolean("user_enabled"))
                        .role(Role.builder()
                                .id(rs.getLong("role_id"))
                                .name(RoleName.valueOf(rs.getString("role_name")))
                                .description(rs.getString("role_description"))
                                .build())
                        .build())
                .station(Station.builder()
                        .id(rs.getLong("station_id"))
                        .name(rs.getString("station_name"))
                        .number(rs.getString("station_number"))
                        .enabled(rs.getBoolean("station_enabled"))
                        .build())
                .initialAmount(rs.getBigDecimal("initial_amount"))
                .openedAt(QueryMapper.mapTimestamp(rs.getTimestamp("opened_at")))
                .closedAt(QueryMapper.mapTimestamp(rs.getTimestamp("closed_at")))
                .build();
    }
}
