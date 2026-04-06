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
            Customer existingCustomer = foundCustomer.get();
            refreshMissingResolvedData(existingCustomer);
            return existingCustomer;
        }

        Customer providedCustomer = findCustomerByDocument(customer.getDocumentType(), customer.getDocumentNumber())
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found with document number: " + customer.getDocumentNumber()));

        providedCustomer.setEnabled(true);

        customerRepository.create(providedCustomer);

        return providedCustomer;
    }

    private void refreshMissingResolvedData(Customer customer) {
        if (customer == null || customer.getId() == null) {
            return;
        }

        boolean shouldRefreshDniData = customer.getDocumentType() == DocumentType.DNI
                && (isBlank(customer.getLegalName())
                || isBlank(customer.getGivenNames())
                || isBlank(customer.getLastName())
                || isBlank(customer.getSecondLastName()));

        if (!shouldRefreshDniData) {
            return;
        }

        findCustomerByDocument(customer.getDocumentType(), customer.getDocumentNumber())
                .ifPresent(resolvedCustomer -> {
                    customerRepository.updateResolvedData(customer.getId(), resolvedCustomer);
                    customer.setLegalName(resolvedCustomer.getLegalName());
                    customer.setGivenNames(resolvedCustomer.getGivenNames());
                    customer.setLastName(resolvedCustomer.getLastName());
                    customer.setSecondLastName(resolvedCustomer.getSecondLastName());
                });
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private Optional<Customer> findCustomerByDocument(DocumentType documentType, String documentNumber) {
        return switch (documentType) {
            case DNI -> customerProvider.findByDni(documentNumber);
            case RUC -> customerProvider.findByRuc(documentNumber);
            default -> throw new InvalidCustomerException("Invalid customer with document type: " + documentType);
        };
    }
}
