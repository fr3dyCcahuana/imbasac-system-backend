package com.paulfernandosr.possystembackend.customer.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.common.domain.Page;
import com.paulfernandosr.possystembackend.common.domain.Pageable;
import com.paulfernandosr.possystembackend.common.infrastructure.mapper.PageMapper;
import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import com.paulfernandosr.possystembackend.customer.application.CustomerCommercialService;
import com.paulfernandosr.possystembackend.customer.domain.Customer;
import com.paulfernandosr.possystembackend.customer.domain.CustomerAddress;
import com.paulfernandosr.possystembackend.customer.infrastructure.adapter.input.dto.CustomerAssignmentRequest;
import com.paulfernandosr.possystembackend.customer.infrastructure.adapter.input.dto.CustomerAssignmentResponse;
import com.paulfernandosr.possystembackend.customer.infrastructure.adapter.input.dto.CustomerCommercialInfoResponse;
import com.paulfernandosr.possystembackend.customer.infrastructure.adapter.input.dto.CustomerContactUpdateResponse;
import com.paulfernandosr.possystembackend.customer.domain.port.input.CreateCustomerAddressUseCase;
import com.paulfernandosr.possystembackend.customer.domain.port.input.CreateNewCustomerUseCase;
import com.paulfernandosr.possystembackend.customer.domain.port.input.GetCustomerInfoUseCase;
import com.paulfernandosr.possystembackend.customer.domain.port.input.GetPageOfCustomersUseCase;
import com.paulfernandosr.possystembackend.customer.domain.port.input.ResolveCustomerUseCase;
import com.paulfernandosr.possystembackend.customer.domain.port.input.UpdateCustomerUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/customers")
public class CustomerRestController {
    private final CreateCustomerAddressUseCase createCustomerAddressUseCase;
    private final CreateNewCustomerUseCase createNewCustomerUseCase;
    private final ResolveCustomerUseCase resolveCustomerUseCase;
    private final GetCustomerInfoUseCase getCustomerInfoUseCase;
    private final GetPageOfCustomersUseCase getPageOfCustomersUseCase;
    private final UpdateCustomerUseCase updateCustomerUseCase;
    private final CustomerCommercialService customerCommercialService;

    @PostMapping
    public ResponseEntity<SuccessResponse<Customer>> createNewCustomer(@RequestBody Customer customer) {
        Customer createdCustomer = createNewCustomerUseCase.createNewCustomer(customer);
        return ResponseEntity.status(HttpStatus.CREATED).body(SuccessResponse.ok(createdCustomer));
    }

    @PostMapping("/{customerId}/addresses")
    public ResponseEntity<SuccessResponse<CustomerAddress>> createCustomerAddress(@PathVariable Long customerId,
                                                                                  @RequestBody CustomerAddress customerAddress) {
        CustomerAddress createdAddress = createCustomerAddressUseCase.createCustomerAddress(customerId, customerAddress);
        return ResponseEntity.status(HttpStatus.CREATED).body(SuccessResponse.ok(createdAddress));
    }

    @PutMapping("/{customerId}")
    public ResponseEntity<SuccessResponse<Customer>> updateCustomer(@PathVariable Long customerId,
                                                                    @RequestBody Customer customer) {
        Customer updatedCustomer = updateCustomerUseCase.updateCustomer(customerId, customer);
        return ResponseEntity.ok(SuccessResponse.ok(updatedCustomer));
    }

    @PostMapping("/resolve")
    public ResponseEntity<SuccessResponse<Customer>> resolveCustomer(@RequestBody Customer customer) {
        return ResponseEntity.ok(SuccessResponse.ok(resolveCustomerUseCase.resolveCustomer(customer)));
    }

    @GetMapping("/{customerId}")
    public ResponseEntity<SuccessResponse<Customer>> getCustomerInfoById(@PathVariable Long customerId) {
        return ResponseEntity.ok(SuccessResponse.ok(getCustomerInfoUseCase.getCustomerInfoById(customerId)));
    }

    @GetMapping("/{customerId}/commercial-info")
    public ResponseEntity<SuccessResponse<CustomerCommercialInfoResponse>> getCommercialInfo(@PathVariable Long customerId) {
        return ResponseEntity.ok(SuccessResponse.ok(customerCommercialService.getCommercialInfo(customerId)));
    }

    @PatchMapping("/{customerId}/contact")
    public ResponseEntity<SuccessResponse<CustomerContactUpdateResponse>> updateContact(@PathVariable Long customerId,
                                                                                        @RequestBody Map<String, Object> patch) {
        return ResponseEntity.ok(SuccessResponse.ok(customerCommercialService.updateContact(customerId, patch)));
    }

    @GetMapping("/{customerId}/assignment")
    public ResponseEntity<SuccessResponse<CustomerAssignmentResponse>> getActiveAssignment(@PathVariable Long customerId) {
        return ResponseEntity.ok(SuccessResponse.ok(customerCommercialService.getActiveAssignment(customerId)));
    }

    @GetMapping("/{customerId}/assignments")
    public ResponseEntity<SuccessResponse<List<CustomerAssignmentResponse>>> getAssignments(@PathVariable Long customerId) {
        return ResponseEntity.ok(SuccessResponse.ok(customerCommercialService.getAssignments(customerId)));
    }

    @PostMapping("/{customerId}/assignments")
    public ResponseEntity<SuccessResponse<CustomerAssignmentResponse>> assign(@PathVariable Long customerId,
                                                                              @RequestBody CustomerAssignmentRequest request,
                                                                              Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SuccessResponse.ok(customerCommercialService.assign(customerId, request, principal)));
    }

    @PostMapping("/{customerId}/assignments/reassign")
    public ResponseEntity<SuccessResponse<CustomerAssignmentResponse>> reassign(@PathVariable Long customerId,
                                                                                @RequestBody CustomerAssignmentRequest request,
                                                                                Principal principal) {
        return ResponseEntity.ok(SuccessResponse.ok(customerCommercialService.reassign(customerId, request, principal)));
    }

    @DeleteMapping("/{customerId}/assignment")
    public ResponseEntity<Void> unassign(@PathVariable Long customerId,
                                         @RequestBody(required = false) CustomerAssignmentRequest request,
                                         Principal principal) {
        customerCommercialService.unassign(customerId, request, principal);
        return ResponseEntity.noContent().build();
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
