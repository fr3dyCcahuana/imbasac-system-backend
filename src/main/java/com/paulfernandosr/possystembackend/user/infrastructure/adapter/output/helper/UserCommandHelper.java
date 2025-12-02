package com.paulfernandosr.possystembackend.user.infrastructure.adapter.output.helper;

import com.paulfernandosr.possystembackend.user.domain.exception.PermissionNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Collection;

@Component
@RequiredArgsConstructor
public class UserCommandHelper {
    private final JdbcClient jdbcClient;

    public void insertUserPermissions(Long userId, Collection<Long> permissionIds) {
        String selectPermissionsByIdSql = "SELECT id FROM permissions WHERE id IN (:permissionIds)";

        Collection<Long> foundPermissionIds = jdbcClient.sql(selectPermissionsByIdSql)
                .param("permissionIds", permissionIds)
                .query(Long.class)
                .set();

        if (permissionIds.size() != foundPermissionIds.size()) {
            throw new PermissionNotFoundException("Permissions not found");
        }

        permissionIds.forEach(permissionId -> {
            String insertUserPermissionsSql = "INSERT INTO user_permissions (user_id, permission_id) VALUES (?, ?)";

            jdbcClient.sql(insertUserPermissionsSql)
                    .params(userId, permissionId)
                    .update();
        });
    }
}
