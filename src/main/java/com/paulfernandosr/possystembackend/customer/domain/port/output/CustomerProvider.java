package com.paulfernandosr.possystembackend.customer.domain.port.output;

import com.paulfernandosr.possystembackend.customer.domain.Customer;

import java.util.Optional;

public interface CustomerProvider {
    Optional<Customer> findByDni(String dni);

    Optional<Customer> findByRuc(String ruc);
}
