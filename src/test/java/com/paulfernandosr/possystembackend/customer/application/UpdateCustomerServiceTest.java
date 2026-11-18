package com.paulfernandosr.possystembackend.customer.application;

import com.paulfernandosr.possystembackend.customer.domain.Customer;
import com.paulfernandosr.possystembackend.customer.domain.CustomerAddress;
import com.paulfernandosr.possystembackend.customer.domain.CustomerEditRevision;
import com.paulfernandosr.possystembackend.customer.domain.DocumentType;
import com.paulfernandosr.possystembackend.customer.domain.port.output.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UpdateCustomerServiceTest {
    private final CustomerRepository repository = mock(CustomerRepository.class);
    private final UpdateCustomerService service = new UpdateCustomerService(repository,
            new CustomerAddressContactValidator());

    @Test
    void missingRevisionCannotReplaceTheCustomer() {
        Customer request = customer();
        assertConflict(request);
        verifyNoInteractions(repository);
    }

    @Test
    void anOldContactSnapshotCannotOverwriteANewerPhone() {
        Customer request = customerWithAddress();
        request.setEditRevision(CustomerEditRevision.calculate(request));
        Customer current = customerWithAddress();
        current.getAddresses().get(0).setPhone("999987654");
        when(repository.findByIdForUpdate(20L)).thenReturn(Optional.of(current));

        assertConflict(request);

        verify(repository, never()).update(any());
        verify(repository, never()).replaceAddresses(anyLong(), anyList());
    }

    @Test
    void anOldAddressListCannotDeleteAnAddressAddedByAnotherUser() {
        Customer request = customerWithAddress();
        request.setEditRevision(CustomerEditRevision.calculate(request));
        Customer current = customerWithAddress();
        current.setAddresses(List.of(current.getAddresses().get(0), address(2L, false)));
        when(repository.findByIdForUpdate(20L)).thenReturn(Optional.of(current));

        assertConflict(request);

        verify(repository, never()).update(any());
        verify(repository, never()).replaceAddresses(anyLong(), anyList());
    }

    @Test
    void anOldHeaderSnapshotCannotOverwriteAnotherUsersChanges() {
        Customer request = customer();
        request.setEditRevision(CustomerEditRevision.calculate(request));
        Customer current = customer();
        current.setLegalName("NOMBRE ACTUALIZADO POR OTRO USUARIO");
        when(repository.findByIdForUpdate(20L)).thenReturn(Optional.of(current));

        assertConflict(request);

        verify(repository, never()).update(any());
    }

    @Test
    void currentRevisionCanSaveAndReturnsTheRefreshedCustomer() {
        Customer current = customerWithAddress();
        Customer request = customerWithAddress();
        request.setEditRevision(CustomerEditRevision.calculate(current));
        request.setLegalName("NUEVO NOMBRE");
        Customer saved = customerWithAddress();
        saved.setLegalName("NUEVO NOMBRE");
        saved.setEditRevision(CustomerEditRevision.calculate(saved));
        when(repository.findByIdForUpdate(20L)).thenReturn(Optional.of(current));
        when(repository.findById(20L)).thenReturn(Optional.of(saved));

        assertThat(service.updateCustomer(20L, request)).isSameAs(saved);

        var order = inOrder(repository);
        order.verify(repository).findByIdForUpdate(20L);
        order.verify(repository).existsByDocumentExcludingId(DocumentType.RUC, "20123456789", 20L);
        order.verify(repository).update(request);
        order.verify(repository).replaceAddresses(20L, request.getAddresses());
        order.verify(repository).findById(20L);
        assertThat(saved.getEditRevision()).isNotEqualTo(CustomerEditRevision.calculate(current));
    }

    @Test
    void customerWithoutAddressesCanExplicitlyClearRootContact() {
        Customer current = customer();
        current.setPhone("999123456");
        current.setEmail("anterior@example.com");
        Customer request = customer();
        request.setEditRevision(CustomerEditRevision.calculate(current));
        when(repository.findByIdForUpdate(20L)).thenReturn(Optional.of(current));
        when(repository.findById(20L)).thenReturn(Optional.of(request));

        service.updateCustomer(20L, request);

        assertThat(request.getPhone()).isNull();
        assertThat(request.getEmail()).isNull();
        verify(repository).update(request);
    }

    @Test
    void customerWithoutAddressesKeepsNormalizedRootContact() {
        Customer current = customer();
        Customer request = customer();
        request.setPhone("+51 999 123 456");
        request.setEmail("  Cliente@Example.COM  ");
        request.setEditRevision(CustomerEditRevision.calculate(current));
        when(repository.findByIdForUpdate(20L)).thenReturn(Optional.of(current));
        when(repository.findById(20L)).thenReturn(Optional.of(request));

        service.updateCustomer(20L, request);

        assertThat(request.getPhone()).isEqualTo("999123456");
        assertThat(request.getEmail()).isEqualTo("cliente@example.com");
    }

    @Test
    void clearingAFiscalContactDoesNotRestoreALegacyRootContact() {
        Customer current = customerWithAddress();
        current.setPhone("999123456");
        current.setEmail("anterior@example.com");
        Customer request = customerWithAddress();
        request.getAddresses().get(0).setPhone(null);
        request.getAddresses().get(0).setEmail(null);
        request.setEditRevision(CustomerEditRevision.calculate(current));
        when(repository.findByIdForUpdate(20L)).thenReturn(Optional.of(current));
        when(repository.findById(20L)).thenReturn(Optional.of(request));

        service.updateCustomer(20L, request);

        assertThat(request.getAddresses().get(0).getPhone()).isNull();
        assertThat(request.getAddresses().get(0).getEmail()).isNull();
        assertThat(request.getPhone()).isNull();
        assertThat(request.getEmail()).isNull();
    }

    private void assertConflict(Customer request) {
        assertThatThrownBy(() -> service.updateCustomer(20L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    private Customer customer() {
        return Customer.builder().id(20L).documentType(DocumentType.RUC)
                .documentNumber("20123456789").legalName("CLIENTE")
                .enabled(true).addresses(List.of()).build();
    }

    private Customer customerWithAddress() {
        Customer customer = customer();
        CustomerAddress address = address(1L, true);
        customer.setAddresses(List.of(address));
        customer.setAddress(address.getAddress());
        customer.setUbigeo(address.getUbigeo());
        customer.setDepartment(address.getDepartment());
        customer.setProvince(address.getProvince());
        customer.setDistrict(address.getDistrict());
        return customer;
    }

    private CustomerAddress address(Long id, boolean fiscal) {
        return CustomerAddress.builder().id(id).customerId(20L).address("DIRECCION " + id)
                .ubigeo("150101").department("LIMA").province("LIMA").district("LIMA")
                .phone("999123456").enabled(true).fiscal(fiscal).position(0).build();
    }
}
