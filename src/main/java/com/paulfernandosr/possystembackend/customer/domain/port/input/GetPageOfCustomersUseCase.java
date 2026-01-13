package com.paulfernandosr.possystembackend.customer.domain.port.input;

import com.paulfernandosr.possystembackend.common.domain.Page;
import com.paulfernandosr.possystembackend.common.domain.Pageable;
import com.paulfernandosr.possystembackend.customer.domain.Customer;

public interface GetPageOfCustomersUseCase {
    Page<Customer> getPageOfCustomers(String query, Pageable pageable);
}
