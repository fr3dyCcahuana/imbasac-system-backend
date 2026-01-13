package com.paulfernandosr.possystembackend.customer.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.common.domain.Page;
import com.paulfernandosr.possystembackend.common.domain.Pageable;
import com.paulfernandosr.possystembackend.common.infrastructure.mapper.PageMapper;
import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import com.paulfernandosr.possystembackend.customer.domain.Customer;
import com.paulfernandosr.possystembackend.customer.domain.port.input.CreateNewCustomerUseCase;
import com.paulfernandosr.possystembackend.customer.domain.port.input.GetCustomerInfoUseCase;
import com.paulfernandosr.possystembackend.customer.domain.port.input.GetPageOfCustomersUseCase;
import com.paulfernandosr.possystembackend.customer.domain.port.input.ResolveCustomerUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;

@RestController
@RequiredArgsConstructor
@RequestMapping("/customers")
public class CustomerRestController {
    private final CreateNewCustomerUseCase createNewCustomerUseCase;
    private final ResolveCustomerUseCase resolveCustomerUseCase;
    private final GetCustomerInfoUseCase getCustomerInfoUseCase;
    private final GetPageOfCustomersUseCase getPageOfCustomersUseCase;

    @PostMapping
    public ResponseEntity<Void> createNewCustomer(@RequestBody Customer customer) {
        createNewCustomerUseCase.createNewCustomer(customer);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @PostMapping("/resolve")
    public ResponseEntity<SuccessResponse<Customer>> resolveCustomer(@RequestBody Customer customer) {
        return ResponseEntity.ok(SuccessResponse.ok(resolveCustomerUseCase.resolveCustomer(customer)));
    }

    @GetMapping("/{customerId}")
    public ResponseEntity<SuccessResponse<Customer>> getCustomerInfoById(@PathVariable Long customerId) {
        return ResponseEntity.ok(SuccessResponse.ok(getCustomerInfoUseCase.getCustomerInfoById(customerId)));
    }

    @GetMapping
    public ResponseEntity<SuccessResponse<Collection<Customer>>> getPageOfCustomers(@RequestParam(defaultValue = "") String query,
                                                                                    @RequestParam(defaultValue = "0") int page,
                                                                                    @RequestParam(defaultValue = "10") int size) {
        Page<Customer> pageOfCustomers = getPageOfCustomersUseCase.getPageOfCustomers(query, new Pageable(page, size));
        SuccessResponse.Metadata metadata = PageMapper.mapPage(pageOfCustomers);
        return ResponseEntity.ok(SuccessResponse.ok(pageOfCustomers.getContent(), metadata));
    }
}
