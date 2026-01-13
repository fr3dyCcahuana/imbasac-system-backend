package com.paulfernandosr.possystembackend.customer.application;

import com.paulfernandosr.possystembackend.customer.domain.Customer;
import com.paulfernandosr.possystembackend.customer.domain.exception.CustomerAlreadyExistsException;
import com.paulfernandosr.possystembackend.customer.domain.port.input.CreateNewCustomerUseCase;
import com.paulfernandosr.possystembackend.customer.domain.port.output.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CreateNewCustomerService implements CreateNewCustomerUseCase {
    private final CustomerRepository customerRepository;

    @Override
    public void createNewCustomer(Customer customer) {
        boolean doesCustomerExists = customerRepository.existsByDocument(customer.getDocumentType(), customer.getDocumentNumber());

        if (doesCustomerExists) {
            throw new CustomerAlreadyExistsException("Customer already exists with document: " + customer.getDocumentType() + customer.getDocumentNumber());
        }

        customerRepository.create(customer);
    }
}
