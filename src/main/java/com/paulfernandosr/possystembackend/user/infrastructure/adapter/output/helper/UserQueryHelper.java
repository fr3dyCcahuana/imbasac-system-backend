package com.paulfernandosr.possystembackend.user.infrastructure.adapter.output.helper;

import com.paulfernandosr.possystembackend.user.domain.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserQueryHelper {
    private final JdbcClient jdbcClient;

    public long getUserIdByUsername(String username) {
        String selectUserIdByUsernameSql = "SELECT id FROM users WHERE username = ?";

        return jdbcClient.sql(selectUserIdByUsernameSql)
                .params(username)
                .query(Long.class)
                .optional()
                .orElseThrow(() -> new UserNotFoundException("User not found with username: " + username));
    }


}
