package com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.common.domain.Page;
import com.paulfernandosr.possystembackend.common.domain.Pageable;
import com.paulfernandosr.possystembackend.common.infrastructure.mapper.QueryMapper;
import com.paulfernandosr.possystembackend.sale.domain.SaleSession;
import com.paulfernandosr.possystembackend.sale.domain.port.output.SaleSessionRepository;
import com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.mapper.FullSaleSessionRowMapper;
import com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.mapper.SaleSessionRowMapper;
import com.paulfernandosr.possystembackend.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PostgresSaleSessionRepository implements SaleSessionRepository {
    private final JdbcClient jdbcClient;

    @Override
    public void create(SaleSession saleSession) {
        String insertSaleSessionSql = """
                    INSERT INTO sale_sessions(
                        user_id,
                        station_id,
                        initial_amount,
                        sales_income,
                        total_discount,
                        total_expenses,
                        opened_at,
                        closed_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

        jdbcClient.sql(insertSaleSessionSql)
                .params(saleSession.getUser().getId(),
                        saleSession.getStation().getId(),
                        saleSession.getInitialAmount(),
                        saleSession.getSalesIncome(),
                        saleSession.getTotalDiscount(),
                        saleSession.getTotalExpenses(),
                        saleSession.getOpenedAt(),
                        saleSession.getClosedAt())
                .update();
    }

    @Override
    public Page<SaleSession> findPage(String query, Pageable pageable) {
        String selectNumberOfSaleSessionsSql = "SELECT COUNT(1) FROM sale_sessions";

        long totalElements = jdbcClient.sql(selectNumberOfSaleSessionsSql)
                .query(Long.class)
                .single();

        String selectPageOfSaleSessionsSql = """
                    SELECT
                        ss.id AS session_id,
                        u.id AS user_id,
                        u.first_name,
                        u.last_name,
                        u.username,
                        u.enabled AS user_enabled,
                        s.id AS station_id,
                        s.name AS station_name,
                        s.number AS station_number,
                        s.enabled AS station_enabled,
                        ss.initial_amount,
                        ss.opened_at,
                        ss.closed_at,
                        r.id AS role_id,
                        r.name AS role_name,
                        r.description AS role_description
                    FROM
                        sale_sessions ss
                    INNER JOIN
                        users u ON u.id = ss.user_id
                    INNER JOIN
                        stations s ON s.id = ss.station_id
                    INNER JOIN
                        roles r ON u.role_id = r.id
                    WHERE
                        u.first_name ILIKE ?
                        OR u.last_name ILIKE ?
                        OR u.username ILIKE ?
                        OR s.name ILIKE ?
                        OR s.number ILIKE ?
                    ORDER BY
                        ss.opened_at DESC
                    LIMIT ?
                    OFFSET ?
                """;

        int pageSize = pageable.getSize();
        int pageNumber = pageable.getNumber();

        Collection<SaleSession> saleSessions = jdbcClient.sql(selectPageOfSaleSessionsSql)
                .params(QueryMapper.formatAsLikeParam(query),
                        QueryMapper.formatAsLikeParam(query),
                        QueryMapper.formatAsLikeParam(query),
                        QueryMapper.formatAsLikeParam(query),
                        QueryMapper.formatAsLikeParam(query),
                        pageSize,
                        pageNumber * pageSize)
                .query(new SaleSessionRowMapper())
                .list();

        BigDecimal totalPages = BigDecimal.valueOf(totalElements)
                .divide(BigDecimal.valueOf(pageSize), 0, RoundingMode.CEILING);

        return Page.<SaleSession>builder()
                .content(saleSessions)
                .number(pageNumber)
                .size(pageSize)
                .numberOfElements(saleSessions.size())
                .totalPages(totalPages.intValue())
                .totalElements(totalElements)
                .build();
    }

    @Override
    public Optional<SaleSession> findById(Long saleSessionId) {
        String selectCashRegisterSessionByIdSql = """
                    SELECT
                        ss.id AS session_id,
                        u.id AS user_id,
                        u.first_name,
                        u.last_name,
                        u.username,
                        r.id AS role_id,
                        r.name AS role_name,
                        r.description AS role_description,
                        u.enabled AS user_enabled,
                        s.id AS station_id,
                        s.name AS station_name,
                        s.number AS station_number,
                        s.enabled AS station_enabled,
                        ss.initial_amount,
                        ss.sales_income,
                        ss.total_discount,
                        ss.total_expenses,
                        ss.opened_at,
                        ss.closed_at
                    FROM
                        sale_sessions ss
                    INNER JOIN
                        users u ON u.id = ss.user_id
                    INNER JOIN
                        roles r ON r.id = u.role_id
                    INNER JOIN
                        stations s ON s.id = ss.station_id
                    WHERE
                        ss.id = ?
                """;

        return jdbcClient.sql(selectCashRegisterSessionByIdSql)
                .param(saleSessionId)
                .query(new FullSaleSessionRowMapper())
                .optional();
    }

    @Override
    public void update(SaleSession saleSession) {
        String updateSaleSessionById = """
                    UPDATE sale_sessions
                    SET user_id = ?,
                        station_id = ?,
                        initial_amount = ?,
                        sales_income = ?,
                        total_discount = ?,
                        total_expenses = ?,
                        opened_at = ?,
                        closed_at = ?
                    WHERE
                        id = ?
                """;

        jdbcClient.sql(updateSaleSessionById)
                .params(saleSession.getUser().getId(),
                        saleSession.getStation().getId(),
                        saleSession.getInitialAmount(),
                        saleSession.getSalesIncome(),
                        saleSession.getTotalDiscount(),
                        saleSession.getTotalExpenses(),
                        saleSession.getOpenedAt(),
                        saleSession.getClosedAt(),
                        saleSession.getId())
                .update();
    }

    @Override
    public void closeById(Long saleSessionId) {
        String updateSaleSessionById = """
                    UPDATE sale_sessions
                    SET closed_at = ?
                    WHERE id = ?
                """;

        jdbcClient.sql(updateSaleSessionById)
                .params(LocalDateTime.now(), saleSessionId)
                .update();
    }

    @Override
    public boolean existsOpenSession(SaleSession saleSession) {
        String existsOpenSessionSql = """
                    SELECT EXISTS(
                        SELECT 1 FROM sale_sessions
                        WHERE (station_id = ? OR user_id = ?)
                        AND closed_at IS NULL
                    )
                """;

        return jdbcClient.sql(existsOpenSessionSql)
                .params(saleSession.getStation().getId(),
                        saleSession.getUser().getId())
                .query(Boolean.class)
                .single();
    }

    @Override
    public boolean existsOpenSessionByUser(User user) {
        String existsByUserIdAndClosedAtIsNullSql = """
                    SELECT EXISTS(
                        SELECT 1 FROM sale_sessions
                        WHERE user_id = ? AND closed_at IS NULL
                    )
                """;

        return jdbcClient.sql(existsByUserIdAndClosedAtIsNullSql)
                .param(user.getId())
                .query(Boolean.class)
                .single();
    }
}
