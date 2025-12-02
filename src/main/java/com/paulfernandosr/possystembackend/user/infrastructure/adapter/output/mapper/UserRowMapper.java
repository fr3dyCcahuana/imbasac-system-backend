package com.paulfernandosr.possystembackend.user.infrastructure.adapter.output.mapper;

import com.paulfernandosr.possystembackend.role.domain.Role;
import com.paulfernandosr.possystembackend.role.domain.RoleName;
import com.paulfernandosr.possystembackend.user.domain.User;
import com.paulfernandosr.possystembackend.user.domain.UserStatus;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class UserRowMapper implements RowMapper<User> {
    @Override
    public User mapRow(ResultSet rs, int rowNum) throws SQLException {
        return User.builder()
                .id(rs.getLong("user_id"))
                .firstName(rs.getString("first_name"))
                .lastName(rs.getString("last_name"))
                .username(rs.getString("username"))
                .role(Role.builder()
                        .id(rs.getLong("role_id"))
                        .name(RoleName.valueOf(rs.getString("role_name")))
                        .description(rs.getString("role_description"))
                        .build())
                .enabled(rs.getBoolean("enabled"))
                .status(UserStatus.valueOf(rs.getString("status")))
                .build();
    }

}
