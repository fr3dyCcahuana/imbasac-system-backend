package com.paulfernandosr.possystembackend.customer.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.common.domain.Page;
import com.paulfernandosr.possystembackend.common.domain.Pageable;
import com.paulfernandosr.possystembackend.common.infrastructure.mapper.QueryMapper;
import com.paulfernandosr.possystembackend.customer.domain.Customer;
import com.paulfernandosr.possystembackend.customer.domain.CustomerAddress;
import com.paulfernandosr.possystembackend.customer.domain.DocumentType;
import com.paulfernandosr.possystembackend.customer.domain.port.output.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
                        ubigeo,
                        department,
                        province,
                        district,
                        sunat_status,
                        sunat_condition,
                        street_type,
                        street_name,
                        zone_code,
                        zone_type,
                        address_number,
                        interior,
                        lot,
                        apartment,
                        block,
                        kilometer,
                        retention_agent,
                        good_contributor,
                        sunat_type,
                        economic_activity,
                        number_of_employees,
                        billing_type,
                        accounting_type,
                        foreign_trade,
                        enabled)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        jdbcClient.sql(insertCustomerSql)
                .params(customer.getLegalName(),
                        customer.getDocumentType().toString(),
                        customer.getDocumentNumber(),
                        customer.getAddress(),
                        customer.getUbigeo(),
                        customer.getDepartment(),
                        customer.getProvince(),
                        customer.getDistrict(),
                        customer.getSunatStatus(),
                        customer.getSunatCondition(),
                        customer.getStreetType(),
                        customer.getStreetName(),
                        customer.getZoneCode(),
                        customer.getZoneType(),
                        customer.getAddressNumber(),
                        customer.getInterior(),
                        customer.getLot(),
                        customer.getApartment(),
                        customer.getBlock(),
                        customer.getKilometer(),
                        customer.isRetentionAgent(),
                        customer.isGoodContributor(),
                        customer.getSunatType(),
                        customer.getEconomicActivity(),
                        customer.getNumberOfEmployees(),
                        customer.getBillingType(),
                        customer.getAccountingType(),
                        customer.getForeignTrade(),
                        customer.isEnabled())
                .update(customerKeyHolder, "id");

        long customerId = Optional.ofNullable(customerKeyHolder.getKey())
                .map(Number::longValue)
                .orElseThrow();

        customer.setId(customerId);

        // Direcciones (fiscal + anexos)
        createCustomerAddresses(customer);
    }

    private void createCustomerAddresses(Customer customer) {
        if (customer.getId() == null) return;

        List<CustomerAddress> addresses = customer.getAddresses();
        if (addresses == null) addresses = new ArrayList<>();

        // Fallback: si no vinieron direcciones pero sí address fiscal legacy
        if (addresses.isEmpty() && customer.getAddress() != null && !customer.getAddress().isBlank()) {
            addresses.add(CustomerAddress.builder()
                    .address(customer.getAddress())
                    .ubigeo(customer.getUbigeo())
                    .department(customer.getDepartment())
                    .province(customer.getProvince())
                    .district(customer.getDistrict())
                    .fiscal(true)
                    .enabled(true)
                    .position(0)
                    .build());
        }

        if (addresses.isEmpty()) return;

        String insertAddressSql = """
                INSERT INTO customer_address(
                    customer_id,
                    address,
                    ubigeo,
                    department,
                    province,
                    district,
                    fiscal,
                    enabled,
                    position
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        for (CustomerAddress address : addresses) {
            if (address == null) continue;
            if (address.getAddress() == null || address.getAddress().isBlank()) continue;

            jdbcClient.sql(insertAddressSql)
                    .params(customer.getId(),
                            address.getAddress(),
                            address.getUbigeo(),
                            address.getDepartment(),
                            address.getProvince(),
                            address.getDistrict(),
                            address.isFiscal(),
                            address.isEnabled(),
                            address.getPosition())
                    .update();
        }
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
                        ubigeo,
                        department,
                        province,
                        district,
                        sunat_status,
                        sunat_condition,
                        street_type,
                        street_name,
                        zone_code,
                        zone_type,
                        address_number,
                        interior,
                        lot,
                        apartment,
                        block,
                        kilometer,
                        retention_agent,
                        good_contributor,
                        sunat_type,
                        economic_activity,
                        number_of_employees,
                        billing_type,
                        accounting_type,
                        foreign_trade,
                        enabled
                    FROM
                        customers
                    WHERE
                        id = ?
                """;

        Optional<Customer> customerOpt = jdbcClient.sql(selectCustomerByIdSql)
                .param(customerId)
                .query(Customer.class)
                .optional();

        customerOpt.ifPresent(this::loadCustomerAddresses);
        return customerOpt;
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
                        ubigeo,
                        department,
                        province,
                        district,
                        sunat_status,
                        sunat_condition,
                        street_type,
                        street_name,
                        zone_code,
                        zone_type,
                        address_number,
                        interior,
                        lot,
                        apartment,
                        block,
                        kilometer,
                        retention_agent,
                        good_contributor,
                        sunat_type,
                        economic_activity,
                        number_of_employees,
                        billing_type,
                        accounting_type,
                        foreign_trade,
                        enabled
                    FROM
                        customers
                    WHERE
                        document_type = ?
                        AND document_number = ?
                """;

        Optional<Customer> customerOpt = jdbcClient.sql(selectCustomerByDocumentNumberSql)
                .params(documentType.toString(), documentNumber)
                .query(Customer.class)
                .optional();

        customerOpt.ifPresent(this::loadCustomerAddresses);
        return customerOpt;
    }

    private void loadCustomerAddresses(Customer customer) {
        if (customer == null || customer.getId() == null) return;

        String selectAddressesSql = """
                SELECT
                    id,
                    customer_id,
                    address,
                    ubigeo,
                    department,
                    province,
                    district,
                    fiscal,
                    enabled,
                    position
                FROM customer_address
                WHERE customer_id = ?
                ORDER BY fiscal DESC, position ASC, id ASC
                """;

        List<CustomerAddress> addresses = jdbcClient.sql(selectAddressesSql)
                .param(customer.getId())
                .query(CustomerAddress.class)
                .list();

        customer.setAddresses(addresses);
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
                        ubigeo,
                        department,
                        province,
                        district,
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
