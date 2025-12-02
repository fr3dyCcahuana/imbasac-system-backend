package com.paulfernandosr.possystembackend.role.infrastructure.adapter.output.helper;

import com.paulfernandosr.possystembackend.user.domain.exception.RoleNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RoleQueryHelper {
    private final JdbcClient jdbcClient;

    public long getRoleIdByName(String roleName) {
        String selectRoleIdByNameSql = "SELECT id FROM roles WHERE name = ?";

        return jdbcClient.sql(selectRoleIdByNameSql)
                .param(roleName)
                .query(Long.class)
                .optional()
                .orElseThrow(() -> new RoleNotFoundException("Role not found with name: " + roleName));
    }
}
