package com.paulfernandosr.possystembackend.customer.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.common.domain.Page;
import com.paulfernandosr.possystembackend.common.domain.Pageable;
import com.paulfernandosr.possystembackend.common.infrastructure.mapper.QueryMapper;
import com.paulfernandosr.possystembackend.customer.domain.Customer;
import com.paulfernandosr.possystembackend.customer.domain.DocumentType;
import com.paulfernandosr.possystembackend.customer.domain.port.output.CustomerRepository;
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
public class PostgresCustomerRepository implements CustomerRepository {
    private final JdbcClient jdbcClient;

    @Override
    public void create(Customer customer) {
        KeyHolder customerKeyHolder = new GeneratedKeyHolder();

        String insertCustomerSql = """
                    INSERT INTO customers(
                        legal_name,
                        document_type,
                        document_number,
                        address,
                        enabled)
                    VALUES (?, ?, ?, ?, ?)
                """;

        jdbcClient.sql(insertCustomerSql)
                .params(customer.getLegalName(),
                        customer.getDocumentType().toString(),
                        customer.getDocumentNumber(),
                        customer.getAddress(),
                        customer.isEnabled())
                .update(customerKeyHolder, "id");

        long customerId = Optional.ofNullable(customerKeyHolder.getKey())
                .map(Number::longValue)
                .orElseThrow();

        customer.setId(customerId);
    }

    @Override
    public Optional<Customer> findById(Long customerId) {
        String selectCustomerByIdSql = """
                    SELECT
                        id,
                        legal_name,
                        document_number,
                        document_type,
                        address,
                        enabled
                    FROM
                        customers
                    WHERE
                        id = ?
                """;

        return jdbcClient.sql(selectCustomerByIdSql)
                .param(customerId)
                .query(Customer.class)
                .optional();
    }

    @Override
    public Optional<Customer> findByDocument(DocumentType documentType, String documentNumber) {
        String selectCustomerByDocumentNumberSql = """
                    SELECT
                        id,
                        legal_name,
                        document_type,
                        document_number,
                        address,
                        enabled
                    FROM
                        customers
                    WHERE
                        document_type = ?
                        AND document_number = ?
                """;

        return jdbcClient.sql(selectCustomerByDocumentNumberSql)
                .params(documentType.toString(), documentNumber)
                .query(Customer.class)
                .optional();
    }

    @Override
    public Page<Customer> findPage(String query, Pageable pageable) {
        String selectNumberOfCustomersSql = "SELECT COUNT(1) FROM customers";

        long totalElements = jdbcClient.sql(selectNumberOfCustomersSql)
                .query(Long.class)
                .single();

        String selectPageOfCustomersSql = """
                    SELECT
                        id,
                        legal_name,
                        document_type,
                        document_number,
                        address,
                        enabled
                    FROM
                        customers
                    WHERE
                        legal_name ILIKE ?
                        OR document_number ILIKE ?
                    ORDER BY
                        legal_name ASC
                    LIMIT ?
                    OFFSET ?
                """;

        int pageSize = pageable.getSize();
        int pageNumber = pageable.getNumber();

        Collection<Customer> customers = jdbcClient.sql(selectPageOfCustomersSql)
                .params(QueryMapper.formatAsLikeParam(query),
                        QueryMapper.formatAsLikeParam(query),
                        pageSize,
                        pageNumber * pageSize)
                .query(Customer.class)
                .list();

        BigDecimal totalPages = BigDecimal.valueOf(totalElements)
                .divide(BigDecimal.valueOf(pageSize), 0, RoundingMode.CEILING);

        return Page.<Customer>builder()
                .content(customers)
                .number(pageNumber)
                .size(pageSize)
                .numberOfElements(customers.size())
                .totalPages(totalPages.intValue())
                .totalElements(totalElements)
                .build();
    }

    @Override
    public boolean existsByDocument(DocumentType documentType, String documentNumber) {
        String selectExistsByDocumentSql = """
                    SELECT EXISTS(
                        SELECT 1 FROM customers
                        WHERE document_type = ? AND document_number = ?
                    )
                """;

        return jdbcClient.sql(selectExistsByDocumentSql)
                .params(documentType.toString(), documentNumber)
                .query(Boolean.class)
                .single();
    }
}
