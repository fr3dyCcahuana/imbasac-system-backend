package com.paulfernandosr.possystembackend.customer.domain.port.input;

import com.paulfernandosr.possystembackend.customer.domain.Customer;

public interface CreateNewCustomerUseCase {
    Customer createNewCustomer(Customer customer);
}
