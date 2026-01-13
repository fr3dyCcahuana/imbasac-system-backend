package com.paulfernandosr.possystembackend.customer.application;

import com.paulfernandosr.possystembackend.common.domain.Page;
import com.paulfernandosr.possystembackend.common.domain.Pageable;
import com.paulfernandosr.possystembackend.customer.domain.Customer;
import com.paulfernandosr.possystembackend.customer.domain.port.input.GetPageOfCustomersUseCase;
import com.paulfernandosr.possystembackend.customer.domain.port.output.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetPageOfCustomersService implements GetPageOfCustomersUseCase {
    private final CustomerRepository customerRepository;

    @Override
    public Page<Customer> getPageOfCustomers(String query, Pageable pageable) {
        return customerRepository.findPage(query, pageable);
    }
}
