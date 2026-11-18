package com.paulfernandosr.possystembackend.customer.application;

import com.paulfernandosr.possystembackend.customer.domain.Customer;
import com.paulfernandosr.possystembackend.customer.domain.CustomerAddress;
import com.paulfernandosr.possystembackend.customer.domain.exception.CustomerAddressAlreadyExistsException;
import com.paulfernandosr.possystembackend.customer.domain.exception.CustomerNotFoundException;
import com.paulfernandosr.possystembackend.customer.domain.exception.InvalidCustomerAddressException;
import com.paulfernandosr.possystembackend.customer.domain.port.input.CreateCustomerAddressUseCase;
import com.paulfernandosr.possystembackend.customer.domain.port.output.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateCustomerAddressService implements CreateCustomerAddressUseCase {
    private final CustomerRepository customerRepository;
    private final CustomerAddressContactValidator contactValidator;

    @Override
    @Transactional
    public CustomerAddress createCustomerAddress(Long customerId, CustomerAddress customerAddress) {
        Customer existing = customerRepository.findByIdForUpdate(customerId)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found with identification: " + customerId));
        boolean firstEnabledAddress = existing.getAddresses() == null
                || existing.getAddresses().stream().noneMatch(address -> address != null && address.isEnabled());
        if (customerAddress != null && firstEnabledAddress) {
            if (isBlank(customerAddress.getPhone())) customerAddress.setPhone(existing.getPhone());
            if (isBlank(customerAddress.getEmail())) customerAddress.setEmail(existing.getEmail());
        }
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

        CustomerAddress created = customerRepository.createAddress(customerId, customerAddress);
        if (firstEnabledAddress) {
            customerRepository.updateContact(customerId, null, null);
        }
        return created;
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

        customerAddress.setPhone(contactValidator.normalizeOptionalPeruvianMobile(customerAddress.getPhone()));
        customerAddress.setEmail(contactValidator.normalizeOptionalEmail(customerAddress.getEmail()));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
