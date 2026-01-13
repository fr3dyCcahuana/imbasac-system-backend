package com.paulfernandosr.possystembackend.supplier.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.common.domain.Page;
import com.paulfernandosr.possystembackend.common.domain.Pageable;
import com.paulfernandosr.possystembackend.common.infrastructure.mapper.QueryMapper;
import com.paulfernandosr.possystembackend.customer.domain.DocumentType;
import com.paulfernandosr.possystembackend.supplier.domain.Supplier;
import com.paulfernandosr.possystembackend.supplier.domain.port.output.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PostgresSupplierRepository implements SupplierRepository {

    private final JdbcClient jdbcClient;

    @Override
    public void create(Supplier supplier) {
        KeyHolder keyHolder = new GeneratedKeyHolder();

        String sql = """
            INSERT INTO suppliers(
                legal_name,
                document_type,
                document_number,
                address,
                enabled
            )
            VALUES (?, ?, ?, ?, ?)
        """;

        jdbcClient.sql(sql)
                .params(
                        supplier.getLegalName(),
                        supplier.getDocumentType().toString(),
                        supplier.getDocumentNumber(),
                        supplier.getAddress(),
                        supplier.isEnabled()
                )
                .update(keyHolder, "id");

        long id = Optional.ofNullable(keyHolder.getKey())
                .map(Number::longValue)
                .orElseThrow();

        supplier.setId(id);
    }

    @Override
    public Optional<Supplier> findById(Long supplierId) {
        String sql = """
            SELECT
                id,
                legal_name,
                document_type,
                document_number,
                address,
                enabled
            FROM suppliers
            WHERE id = ?
        """;

        return jdbcClient.sql(sql)
                .param(supplierId)
                .query(Supplier.class)
                .optional();
    }

    @Override
    public Optional<Supplier> findByDocument(DocumentType documentType, String documentNumber) {
        String sql = """
            SELECT
                id,
                legal_name,
                document_type,
                document_number,
                address,
                enabled
            FROM suppliers
            WHERE document_type = ?
              AND document_number = ?
        """;

        return jdbcClient.sql(sql)
                .params(documentType.toString(), documentNumber)
                .query(Supplier.class)
                .optional();
    }

    @Override
    public Page<Supplier> findPage(String query, Pageable pageable) {
        String countSql = "SELECT COUNT(1) FROM suppliers";
        long totalElements = jdbcClient.sql(countSql).query(Long.class).single();

        String pageSql = """
            SELECT
                id,
                legal_name,
                document_type,
                document_number,
                address,
                enabled
            FROM suppliers
            WHERE legal_name ILIKE ?
               OR document_number ILIKE ?
            ORDER BY legal_name ASC
            LIMIT ?
            OFFSET ?
        """;

        int size = pageable.getSize();
        int number = pageable.getNumber();

        Collection<Supplier> suppliers = jdbcClient.sql(pageSql)
                .params(
                        QueryMapper.formatAsLikeParam(query),
                        QueryMapper.formatAsLikeParam(query),
                        size,
                        number * size
                )
                .query(Supplier.class)
                .list();

        BigDecimal totalPages = BigDecimal.valueOf(totalElements)
                .divide(BigDecimal.valueOf(size), 0, RoundingMode.CEILING);

        return Page.<Supplier>builder()
                .content(suppliers)
                .number(number)
                .size(size)
                .numberOfElements(suppliers.size())
                .totalPages(totalPages.intValue())
                .totalElements(totalElements)
                .build();
    }

    @Override
    public boolean existsByDocument(DocumentType documentType, String documentNumber) {
        String sql = """
            SELECT EXISTS(
                SELECT 1 FROM suppliers
                WHERE document_type = ? AND document_number = ?
            )
        """;

        return jdbcClient.sql(sql)
                .params(documentType.toString(), documentNumber)
                .query(Boolean.class)
                .single();
    }
}
