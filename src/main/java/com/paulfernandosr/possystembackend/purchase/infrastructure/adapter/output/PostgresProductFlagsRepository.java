package com.paulfernandosr.possystembackend.purchase.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.purchase.domain.model.ProductFlags;
import com.paulfernandosr.possystembackend.purchase.domain.port.output.ProductFlagsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PostgresProductFlagsRepository implements ProductFlagsRepository {

    private final JdbcClient jdbcClient;

    @Override
    public Optional<ProductFlags> findById(Long productId) {
        String sql = """
            SELECT id, manage_by_serial, affects_stock, category
            FROM product
            WHERE id = ?
            """;

        return jdbcClient.sql(sql)
                .param(productId)
                .query(rs -> {
                    if (!rs.next()) return Optional.empty();
                    return Optional.of(ProductFlags.builder()
                            .id(rs.getLong("id"))
                            .manageBySerial(rs.getObject("manage_by_serial", Boolean.class))
                            .affectsStock(rs.getObject("affects_stock", Boolean.class))
                            .category(rs.getString("category"))
                            .build());
                });
    }
}
