package com.paulfernandosr.possystembackend.customer.domain.port.input;

import com.paulfernandosr.possystembackend.customer.domain.Customer;

public interface ResolveCustomerUseCase {
    Customer resolveCustomer(Customer customer);
}
