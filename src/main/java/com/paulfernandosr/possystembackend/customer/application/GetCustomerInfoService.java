package com.paulfernandosr.possystembackend.customer.application;

import com.paulfernandosr.possystembackend.customer.domain.Customer;
import com.paulfernandosr.possystembackend.customer.domain.exception.CustomerNotFoundException;
import com.paulfernandosr.possystembackend.customer.domain.port.input.GetCustomerInfoUseCase;
import com.paulfernandosr.possystembackend.customer.domain.port.output.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetCustomerInfoService implements GetCustomerInfoUseCase {
    private final CustomerRepository customerRepository;

    @Override
    public Customer getCustomerInfoById(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found with identification: " + customerId));
    }
}
