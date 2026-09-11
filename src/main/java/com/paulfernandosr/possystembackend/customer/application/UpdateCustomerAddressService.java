package com.paulfernandosr.possystembackend.customer.application;

import com.paulfernandosr.possystembackend.customer.domain.CustomerAddress;
import com.paulfernandosr.possystembackend.customer.domain.exception.CustomerNotFoundException;
import com.paulfernandosr.possystembackend.customer.domain.exception.InvalidCustomerAddressException;
import com.paulfernandosr.possystembackend.customer.domain.port.input.UpdateCustomerAddressUseCase;
import com.paulfernandosr.possystembackend.customer.domain.port.output.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UpdateCustomerAddressService implements UpdateCustomerAddressUseCase {
    private final CustomerRepository customerRepository;
    private final CustomerAddressContactValidator contactValidator;

    @Override
    public CustomerAddress updateCustomerAddress(Long customerId, Long addressId, CustomerAddress customerAddress) {
        if (!customerRepository.existsById(customerId)) {
            throw new CustomerNotFoundException("Customer not found with identification: " + customerId);
        }

        validateCustomerAddress(addressId, customerAddress);

        customerAddress.setId(addressId);
        customerAddress.setCustomerId(customerId);
        customerAddress.setEnabled(true);

        return customerRepository.updateAddress(customerId, addressId, customerAddress);
    }

    private void validateCustomerAddress(Long addressId, CustomerAddress customerAddress) {
        if (addressId == null) {
            throw new InvalidCustomerAddressException("Customer address id is required");
        }

        if (customerAddress == null) {
            throw new InvalidCustomerAddressException("Customer address is required");
        }

        if (isBlank(customerAddress.getAddress())) {
            throw new InvalidCustomerAddressException("Customer address is required");
        }

        if (isBlank(customerAddress.getUbigeo())) {
            throw new InvalidCustomerAddressException("Customer address ubigeo is required");
        }

        if (customerAddress.getUbigeo().length() != 6) {
            throw new InvalidCustomerAddressException("Customer address ubigeo must have 6 digits");
        }

        customerAddress.setPhone(contactValidator.normalizeOptionalPeruvianMobile(customerAddress.getPhone()));
        customerAddress.setEmail(contactValidator.normalizeOptionalEmail(customerAddress.getEmail()));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
