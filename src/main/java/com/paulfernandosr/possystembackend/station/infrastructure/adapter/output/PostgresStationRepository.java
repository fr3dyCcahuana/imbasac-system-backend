package com.paulfernandosr.possystembackend.station.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.common.infrastructure.mapper.QueryMapper;
import com.paulfernandosr.possystembackend.station.domain.Station;
import com.paulfernandosr.possystembackend.station.domain.StationStatus;
import com.paulfernandosr.possystembackend.station.domain.port.output.StationRepository;
import com.paulfernandosr.possystembackend.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PostgresStationRepository implements StationRepository {
    private final JdbcClient jdbcClient;

    @Override
    public void create(Station station) {
        String insertStationSql = """
                    INSERT INTO stations(name, number, enabled)
                    VALUES (?, ?, ?)
                """;

        jdbcClient.sql(insertStationSql)
                .params(station.getName(),
                        station.getNumber(),
                        station.isEnabled())
                .update();
    }

    @Override
    public Optional<Station> findById(Long id) {
        String selectStationByIdSql = """
                    SELECT
                        s.id,
                        s.name,
                        s.number,
                        s.enabled,
                        CASE
                            WHEN s.enabled = FALSE THEN 'DISABLED'
                            WHEN ss.id IS NOT NULL THEN 'OPEN'
                            ELSE 'CLOSED'
                        END AS status
                    FROM
                        stations s
                    LEFT JOIN
                        sale_sessions ss ON s.id = ss.station_id
                        AND ss.closed_at IS NULL
                    WHERE
                        s.id = ?
                """;

        return jdbcClient.sql(selectStationByIdSql)
                .param(id)
                .query(Station.class)
                .optional();
    }

    @Override
    public Optional<Station> findByUserOnRegister(User user) {
        String selectStationByUserInOpenSaleSession = """
                    SELECT
                        s.id,
                        s.name,
                        s.number,
                        s.enabled
                    FROM
                        stations s
                    INNER JOIN
                        sale_sessions ss ON ss.station_id = s.id
                    WHERE
                        ss.user_id = ?
                        AND ss.closed_at IS NULL
                """;

        return jdbcClient.sql(selectStationByUserInOpenSaleSession)
                .param(user.getId())
                .query(Station.class)
                .optional();
    }

    @Override
    public Collection<Station> findAll(StationStatus status) {
        String selectAllStationsSql = """
                    WITH stations AS (
                        SELECT
                            s.id,
                            s.name,
                            s.number,
                            s.enabled,
                            CASE
                                WHEN s.enabled = FALSE THEN 'DISABLED'
                                WHEN ss.id IS NOT NULL THEN 'OPEN'
                                ELSE 'CLOSED'
                            END AS status
                        FROM
                            stations s
                        LEFT JOIN
                            sale_sessions ss ON s.id = ss.station_id
                            AND ss.closed_at IS NULL
                    )
                    SELECT * FROM stations
                    WHERE status = COALESCE(?, status)
                """;

        return jdbcClient.sql(selectAllStationsSql)
                .param(QueryMapper.mapEnum(status))
                .query(Station.class)
                .list();
    }
}
