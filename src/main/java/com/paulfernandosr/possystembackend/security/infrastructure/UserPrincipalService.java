package com.paulfernandosr.possystembackend.security.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserPrincipalService implements UserDetailsService {
    private final JdbcClient jdbcClient;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserPrincipal userPrincipal = getUserCredentials(username)
                .orElseThrow(() -> new UsernameNotFoundException("User principal not found with username: " + username));

        userPrincipal.setAuthorities(getUserPermissions(username));

        return userPrincipal;
    }

    private Optional<UserPrincipal> getUserCredentials(String username) {
        String selectUserCredentialsByUsernameSql = """
                    SELECT username, password, enabled
                    FROM users WHERE username = ?
                """;

        return jdbcClient.sql(selectUserCredentialsByUsernameSql)
                .param(username)
                .query(UserPrincipal.class)
                .optional();
    }

    public Set<SimpleGrantedAuthority> getUserPermissions(String username) {
        String selectUserPermissionsByUsernameSql = """
                    SELECT p.name AS permission
                    FROM permissions p
                    INNER JOIN user_permissions up ON p.id = up.permission_id
                    INNER JOIN users u ON u.id = up.user_id
                    WHERE u.username = ?
                
                    UNION ALL
                
                    SELECT p.name AS permission
                    FROM permissions p
                    INNER JOIN role_permissions rp ON p.id = rp.permission_id
                    INNER JOIN users u ON u.role_id = rp.role_id
                    WHERE u.username = ?
                """;

        return jdbcClient.sql(selectUserPermissionsByUsernameSql)
                .params(username, username)
                .query((rs, rowNum) -> new SimpleGrantedAuthority(rs.getString("permission")))
                .set();
    }
}
