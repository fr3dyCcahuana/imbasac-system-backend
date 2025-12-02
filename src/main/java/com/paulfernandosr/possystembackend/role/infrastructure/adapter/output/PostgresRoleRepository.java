package com.paulfernandosr.possystembackend.role.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.permission.domain.Permission;
import com.paulfernandosr.possystembackend.role.domain.port.output.RoleRepository;
import com.paulfernandosr.possystembackend.role.domain.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PostgresRoleRepository implements RoleRepository {
    private final JdbcClient jdbcClient;

    @Override
    public Optional<Role> findById(Long roleId) {
        String selectRoleByIdSql = "SELECT id, name, description FROM roles WHERE id = ?";

        return jdbcClient.sql(selectRoleByIdSql)
                .param(roleId)
                .query(Role.class)
                .optional();
    }

    @Override
    public Collection<Role> findAll() {
        String selectAllRolesSql = "SELECT id, name, description FROM roles";

        return jdbcClient.sql(selectAllRolesSql)
                .query(Role.class)
                .list();
    }

    @Override
    public Collection<Permission> findRolePermissionsByRoleId(Long roleId) {
        String selectRolePermissionsByRoleIdSql = """
                    SELECT
                        p.id,
                        p.name,
                        p.domain,
                        p.description
                    FROM roles r
                    INNER JOIN role_permissions rp ON rp.role_id = r.id
                    INNER JOIN permissions p ON p.id = rp.permission_id
                    WHERE r.id = ?
                """;

        return jdbcClient.sql(selectRolePermissionsByRoleIdSql)
                .param(roleId)
                .query(Permission.class)
                .set();
    }
}
