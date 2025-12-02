package com.paulfernandosr.possystembackend.permission.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.permission.domain.Permission;
import com.paulfernandosr.possystembackend.permission.domain.port.output.PermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Collection;

@Repository
@RequiredArgsConstructor
public class PostgresPermissionRepository implements PermissionRepository {
    private final JdbcClient jdbcClient;

    @Override
    public Collection<Permission> findAll() {
        String selectAllPermissionSql = """
                    SELECT
                        id,
                        name,
                        domain,
                        description
                    FROM
                        permissions
                """;

        return jdbcClient.sql(selectAllPermissionSql)
                .query(Permission.class)
                .list();
    }
}
