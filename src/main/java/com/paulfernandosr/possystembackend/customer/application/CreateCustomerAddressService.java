package com.paulfernandosr.possystembackend.customer.application;

import com.paulfernandosr.possystembackend.customer.domain.CustomerAddress;
import com.paulfernandosr.possystembackend.customer.domain.exception.CustomerAddressAlreadyExistsException;
import com.paulfernandosr.possystembackend.customer.domain.exception.CustomerNotFoundException;
import com.paulfernandosr.possystembackend.customer.domain.exception.InvalidCustomerAddressException;
import com.paulfernandosr.possystembackend.customer.domain.port.input.CreateCustomerAddressUseCase;
import com.paulfernandosr.possystembackend.customer.domain.port.output.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CreateCustomerAddressService implements CreateCustomerAddressUseCase {
    private final CustomerRepository customerRepository;

    @Override
    public CustomerAddress createCustomerAddress(Long customerId, CustomerAddress customerAddress) {
        validateCustomerExists(customerId);
        validateCustomerAddress(customerAddress);

        if (customerRepository.existsAddress(customerId, customerAddress.getAddress(), customerAddress.getUbigeo())) {
            throw new CustomerAddressAlreadyExistsException(
                    "Customer already has an address registered with the same address and ubigeo"
            );
        }

        if (customerAddress.isFiscal() && customerRepository.hasFiscalAddress(customerId)) {
            throw new InvalidCustomerAddressException(
                    "Customer already has an enabled fiscal address"
            );
        }

        customerAddress.setCustomerId(customerId);
        customerAddress.setEnabled(true);

        return customerRepository.createAddress(customerId, customerAddress);
    }

    private void validateCustomerExists(Long customerId) {
        if (!customerRepository.existsById(customerId)) {
            throw new CustomerNotFoundException("Customer not found with identification: " + customerId);
        }
    }

    private void validateCustomerAddress(CustomerAddress customerAddress) {
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
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
