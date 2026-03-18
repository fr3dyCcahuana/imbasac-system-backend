package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.salev2.domain.model.OpenSaleSession;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.SaleSessionControlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PostgresSaleSessionControlRepository implements SaleSessionControlRepository {

    private final JdbcClient jdbcClient;

    @Override
    public OpenSaleSession findOpenByUserId(Long userId) {
        String sql = """
            SELECT id, user_id, station_id
              FROM sale_sessions
             WHERE user_id = ?
               AND closed_at IS NULL
             ORDER BY opened_at DESC
             LIMIT 1
             FOR UPDATE
        """;

        return jdbcClient.sql(sql)
                .param(userId)
                .query((rs, rowNum) -> OpenSaleSession.builder()
                        .id(rs.getLong("id"))
                        .userId(rs.getLong("user_id"))
                        .stationId(rs.getLong("station_id"))
                        .build())
                .optional()
                .orElse(null);
    }
}
