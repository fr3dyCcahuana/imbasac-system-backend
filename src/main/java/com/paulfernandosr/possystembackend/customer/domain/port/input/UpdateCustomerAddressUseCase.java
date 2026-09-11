package com.paulfernandosr.possystembackend.customer.domain.port.input;

import com.paulfernandosr.possystembackend.customer.domain.CustomerAddress;

public interface UpdateCustomerAddressUseCase {
    CustomerAddress updateCustomerAddress(Long customerId, Long addressId, CustomerAddress customerAddress);
}
