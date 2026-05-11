package com.paulfernandosr.possystembackend.customer.domain.port.input;

import com.paulfernandosr.possystembackend.customer.domain.Customer;

public interface UpdateCustomerUseCase {
    Customer updateCustomer(Long customerId, Customer customer);
}
