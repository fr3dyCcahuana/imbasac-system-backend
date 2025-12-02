package com.paulfernandosr.possystembackend.user.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.common.infrastructure.mapper.QueryMapper;
import com.paulfernandosr.possystembackend.permission.domain.Permission;
import com.paulfernandosr.possystembackend.user.domain.port.output.UserRepository;
import com.paulfernandosr.possystembackend.user.domain.*;
import com.paulfernandosr.possystembackend.user.infrastructure.adapter.output.helper.UserCommandHelper;
import com.paulfernandosr.possystembackend.user.infrastructure.adapter.output.mapper.UserRowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class PostgresUserRepository implements UserRepository {
    private final JdbcClient jdbcClient;
    private final UserCommandHelper userCommandHelper;

    @Override
    @Transactional
    public void create(User user) {
        KeyHolder userKeyHolder = new GeneratedKeyHolder();

        String insertUserSql = """
                    INSERT INTO users(
                        first_name,
                        last_name,
                        username,
                        password,
                        role_id,
                        enabled)
                    VALUES (?, ?, ?, ? ,? ,?)
                """;

        jdbcClient.sql(insertUserSql)
                .params(user.getFirstName(),
                        user.getLastName(),
                        user.getUsername(),
                        user.getPassword(),
                        user.getRole().getId(),
                        user.isEnabled())
                .update(userKeyHolder, "id");

        if (user.getPermissions().isEmpty()) {
            return;
        }

        long userId = Optional.ofNullable(userKeyHolder.getKey())
                .map(Number::longValue)
                .orElseThrow();

        Set<Long> permissionIds = user.getPermissions()
                .stream()
                .map(Permission::getId)
                .collect(Collectors.toUnmodifiableSet());

        userCommandHelper.insertUserPermissions(userId, permissionIds);
    }

    @Override
    public Optional<User> findById(Long userId) {
        String selectUserByIdSql = """
                    SELECT
                        u.id AS user_id,
                        u.first_name,
                        u.last_name,
                        u.username,
                        r.id AS role_id,
                        r.name AS role_name,
                        r.description AS role_description,
                        u.enabled,
                        CASE
                            WHEN u.enabled = FALSE THEN 'DISABLED'
                            WHEN ss.id IS NOT NULL THEN 'ON_REGISTER'
                            ELSE 'OFF_REGISTER'
                        END AS status
                    FROM
                        users u
                    INNER JOIN
                        roles r ON r.id = u.role_id
                    LEFT JOIN
                        sale_sessions ss ON u.id = ss.user_id AND ss.closed_at IS NULL
                    WHERE
                        u.id = ?
                """;

        return jdbcClient.sql(selectUserByIdSql)
                .param(userId)
                .query(new UserRowMapper())
                .optional();
    }

    @Override
    public Optional<User> findByUsername(String username) {
        String selectUserByUsernameSql = """
                    SELECT
                        u.id AS user_id,
                        u.first_name,
                        u.last_name,
                        u.username,
                        r.id AS role_id,
                        r.name AS role_name,
                        r.description AS role_description,
                        u.enabled,
                        CASE
                            WHEN u.enabled = FALSE THEN 'DISABLED'
                            WHEN ss.id IS NOT NULL THEN 'ON_REGISTER'
                            ELSE 'OFF_REGISTER'
                        END AS status
                    FROM users u
                    INNER JOIN roles r ON r.id = u.role_id
                    LEFT JOIN sale_sessions ss ON u.id = ss.user_id AND ss.closed_at IS NULL
                    WHERE u.username = ?
                """;

        return jdbcClient.sql(selectUserByUsernameSql)
                .param(username)
                .query(new UserRowMapper())
                .optional();
    }

    @Override
    public Collection<Permission> findUserPermissionsByUserId(Long userId) {
        String selectUserPermissionsByUsernameSql = """
                    SELECT
                        p.id,
                        p.name,
                        p.domain,
                        p.description
                    FROM users u
                    INNER JOIN user_permissions up ON up.user_id = u.id
                    INNER JOIN permissions p ON p.id = up.permission_id
                    WHERE u.id = ?
                """;

        return jdbcClient.sql(selectUserPermissionsByUsernameSql)
                .param(userId)
                .query(Permission.class)
                .list();
    }

    @Override
    public Collection<User> findAll(UserStatus status) {
        String selectAllUsersSql = """
                    WITH users AS (
                        SELECT
                            u.id AS user_id,
                            u.first_name,
                            u.last_name,
                            u.username,
                            r.id AS role_id,
                            r.name AS role_name,
                            r.description AS role_description,
                            u.enabled,
                            CASE
                                WHEN ss.id IS NOT NULL THEN 'ON_REGISTER'
                                ELSE 'OFF_REGISTER'
                            END AS status
                        FROM
                            users u
                        INNER JOIN
                            roles r ON u.role_id = r.id
                        LEFT JOIN
                            sale_sessions ss ON u.id = ss.user_id AND ss.closed_at IS NULL
                    )
                    SELECT * FROM users
                    WHERE status = COALESCE(?, status)
                """;

        return jdbcClient.sql(selectAllUsersSql)
                .param(QueryMapper.mapEnum(status))
                .query(new UserRowMapper())
                .list();
    }

    @Override
    @Transactional
    public void updateById(Long userId, User user) {
        String updateUserByIdSql = """
                    UPDATE users
                    SET first_name = ?,
                        last_name = ?,
                        username = ?,
                        password = COALESCE(NULLIF(?, ''), password),
                        role_id = ?,
                        enabled = ?
                    WHERE id = ?
                """;

        jdbcClient.sql(updateUserByIdSql)
                .params(user.getFirstName(),
                        user.getLastName(),
                        user.getUsername(),
                        user.getPassword(),
                        user.getRole().getId(),
                        user.isEnabled(),
                        userId)
                .update();

        String deleteUserPermissionsByUserIdSql = "DELETE FROM user_permissions WHERE user_id = ?";

        jdbcClient.sql(deleteUserPermissionsByUserIdSql)
                .param(userId)
                .update();

        if (user.getPermissions().isEmpty()) {
            return;
        }

        Set<Long> permissionIds = user.getPermissions()
                .stream()
                .map(Permission::getId)
                .collect(Collectors.toUnmodifiableSet());

        userCommandHelper.insertUserPermissions(userId, permissionIds);
    }

    @Override
    public void enableById(Long userId) {
        String enableUserByIdSql = "UPDATE users SET enabled = true WHERE id = ?";

        jdbcClient.sql(enableUserByIdSql)
                .param(userId)
                .update();
    }

    @Override
    public void disableById(Long userId) {
        String disableUserByIdSql = "UPDATE users SET enabled = false WHERE id = ?";

        jdbcClient.sql(disableUserByIdSql)
                .param(userId)
                .update();
    }
}
