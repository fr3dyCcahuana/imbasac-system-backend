package com.paulfernandosr.possystembackend.customer.domain.port.input;

import com.paulfernandosr.possystembackend.customer.domain.CustomerAddress;

public interface CreateCustomerAddressUseCase {
    CustomerAddress createCustomerAddress(Long customerId, CustomerAddress customerAddress);
}
