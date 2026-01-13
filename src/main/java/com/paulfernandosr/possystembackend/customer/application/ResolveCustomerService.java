package com.paulfernandosr.possystembackend.customer.application;

import com.paulfernandosr.possystembackend.customer.domain.Customer;
import com.paulfernandosr.possystembackend.customer.domain.DocumentType;
import com.paulfernandosr.possystembackend.customer.domain.exception.CustomerNotFoundException;
import com.paulfernandosr.possystembackend.customer.domain.exception.InvalidCustomerException;
import com.paulfernandosr.possystembackend.customer.domain.port.input.ResolveCustomerUseCase;
import com.paulfernandosr.possystembackend.customer.domain.port.output.CustomerProvider;
import com.paulfernandosr.possystembackend.customer.domain.port.output.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ResolveCustomerService implements ResolveCustomerUseCase {
    private final CustomerRepository customerRepository;
    private final CustomerProvider customerProvider;

    @Override
    public Customer resolveCustomer(Customer customer) {
        Optional<Customer> foundCustomer = customerRepository.findByDocument(customer.getDocumentType(), customer.getDocumentNumber());

        if (foundCustomer.isPresent()) {
            return foundCustomer.get();
        }

        Customer providedCustomer = findCustomerByDocument(customer.getDocumentType(), customer.getDocumentNumber())
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found with document number: " + customer.getDocumentNumber()));

        providedCustomer.setEnabled(true);

        customerRepository.create(providedCustomer);

        return providedCustomer;
    }

    private Optional<Customer> findCustomerByDocument(DocumentType documentType, String documentNumber) {
        return switch (documentType) {
            case DNI -> customerProvider.findByDni(documentNumber);
            case RUC -> customerProvider.findByRuc(documentNumber);
            default -> throw new InvalidCustomerException("Invalid customer with document type: " + documentType);
        };
    }
}
