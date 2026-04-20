package com.paulfernandosr.possystembackend.customer.domain.port.output;

import com.paulfernandosr.possystembackend.common.domain.Page;
import com.paulfernandosr.possystembackend.common.domain.Pageable;
import com.paulfernandosr.possystembackend.customer.domain.Customer;
import com.paulfernandosr.possystembackend.customer.domain.CustomerAddress;
import com.paulfernandosr.possystembackend.customer.domain.DocumentType;

import java.util.Optional;

public interface CustomerRepository {
    void create(Customer customer);

    Optional<Customer> findById(Long customerId);

    Optional<Customer> findByDocument(DocumentType documentType, String documentNumber);

    Page<Customer> findPage(String query, Pageable pageable);

    boolean existsByDocument(DocumentType documentType, String documentNumber);

    boolean existsById(Long customerId);

    boolean existsAddress(Long customerId, String address, String ubigeo);

    boolean hasFiscalAddress(Long customerId);

    CustomerAddress createAddress(Long customerId, CustomerAddress customerAddress);

    void updateResolvedData(Long customerId, Customer customer);
}
