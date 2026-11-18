package com.paulfernandosr.possystembackend.customer.application;

import com.paulfernandosr.possystembackend.customer.domain.Customer;
import com.paulfernandosr.possystembackend.customer.domain.CustomerAddress;
import com.paulfernandosr.possystembackend.customer.domain.exception.CustomerNotFoundException;
import com.paulfernandosr.possystembackend.customer.domain.port.output.CustomerRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CustomerAddressPersistenceServiceTest {
    private final CustomerRepository repository = mock(CustomerRepository.class);
    private final CustomerAddressContactValidator validator = new CustomerAddressContactValidator();

    @Test
    void firstFiscalAddressReceivesTheContactSavedWithoutAnAddress() {
        Customer current = Customer.builder().id(20L).phone("999123456")
                .email("cliente@example.com").addresses(List.of()).build();
        when(repository.findByIdForUpdate(20L)).thenReturn(Optional.of(current));
        CustomerAddress request = address(true);
        when(repository.createAddress(20L, request)).thenReturn(request);

        CustomerAddress saved = new CreateCustomerAddressService(repository, validator)
                .createCustomerAddress(20L, request);

        assertThat(saved.getPhone()).isEqualTo("999123456");
        assertThat(saved.getEmail()).isEqualTo("cliente@example.com");
        var order = inOrder(repository);
        order.verify(repository).findByIdForUpdate(20L);
        order.verify(repository).existsAddress(20L, "DIRECCION", "150101");
        order.verify(repository).hasFiscalAddress(20L);
        order.verify(repository).createAddress(20L, request);
        order.verify(repository).updateContact(20L, null, null);
    }

    @Test
    void firstAddressPreservesContactExplicitlyEnteredForThatAddress() {
        Customer current = Customer.builder().id(20L).phone("999123456")
                .email("cliente@example.com").addresses(List.of()).build();
        when(repository.findByIdForUpdate(20L)).thenReturn(Optional.of(current));
        CustomerAddress request = address(false);
        request.setPhone("999987654");
        request.setEmail("direccion@example.com");
        when(repository.createAddress(20L, request)).thenReturn(request);

        new CreateCustomerAddressService(repository, validator).createCustomerAddress(20L, request);

        assertThat(request.getPhone()).isEqualTo("999987654");
        assertThat(request.getEmail()).isEqualTo("direccion@example.com");
        assertThat(request.isFiscal()).isFalse();
        verify(repository).updateContact(20L, null, null);
    }

    @Test
    void firstNonFiscalAddressReceivesRootContactBeforeRootIsCleared() {
        CustomerAddress disabled = address(true);
        disabled.setEnabled(false);
        Customer current = Customer.builder().id(20L).phone("999123456")
                .email("cliente@example.com").addresses(List.of(disabled)).build();
        when(repository.findByIdForUpdate(20L)).thenReturn(Optional.of(current));
        CustomerAddress request = address(false);
        when(repository.createAddress(20L, request)).thenReturn(request);

        CustomerAddress saved = new CreateCustomerAddressService(repository, validator)
                .createCustomerAddress(20L, request);

        assertThat(saved.getPhone()).isEqualTo("999123456");
        assertThat(saved.getEmail()).isEqualTo("cliente@example.com");
        assertThat(saved.isFiscal()).isFalse();
        var order = inOrder(repository);
        order.verify(repository).createAddress(20L, request);
        order.verify(repository).updateContact(20L, null, null);
        verify(repository, never()).hasFiscalAddress(anyLong());
    }

    @Test
    void failedFirstAddressCreationDoesNotClearRootContact() {
        Customer current = Customer.builder().id(20L).phone("999123456")
                .email("cliente@example.com").addresses(List.of()).build();
        when(repository.findByIdForUpdate(20L)).thenReturn(Optional.of(current));
        CustomerAddress request = address(false);
        when(repository.createAddress(20L, request)).thenThrow(new IllegalStateException("insert failed"));

        assertThatThrownBy(() -> new CreateCustomerAddressService(repository, validator)
                .createCustomerAddress(20L, request))
                .isInstanceOf(IllegalStateException.class).hasMessage("insert failed");

        verify(repository, never()).updateContact(anyLong(), any(), any());
        assertThat(current.getPhone()).isEqualTo("999123456");
        assertThat(current.getEmail()).isEqualTo("cliente@example.com");
    }

    @Test
    void aFurtherAddressDoesNotInheritOrClearRootContact() {
        CustomerAddress enabled = address(true);
        enabled.setEnabled(true);
        Customer current = Customer.builder().id(20L).phone("999123456")
                .email("cliente@example.com").addresses(List.of(enabled)).build();
        when(repository.findByIdForUpdate(20L)).thenReturn(Optional.of(current));
        CustomerAddress request = address(false);
        when(repository.createAddress(20L, request)).thenReturn(request);

        CustomerAddress saved = new CreateCustomerAddressService(repository, validator)
                .createCustomerAddress(20L, request);

        assertThat(saved.getPhone()).isNull();
        assertThat(saved.getEmail()).isNull();
        verify(repository, never()).updateContact(anyLong(), any(), any());
    }

    @Test
    void updatingAnExistingAddressLocksTheCustomerBeforeSavingAndAllowsContactRemoval() {
        Customer current = Customer.builder().id(20L).phone("999123456")
                .addresses(List.of(address(true))).build();
        when(repository.findByIdForUpdate(20L)).thenReturn(Optional.of(current));
        CustomerAddress request = address(true);
        when(repository.updateAddress(20L, 1L, request)).thenReturn(request);

        CustomerAddress saved = new UpdateCustomerAddressService(repository, validator)
                .updateCustomerAddress(20L, 1L, request);

        assertThat(saved.getPhone()).isNull();
        var order = inOrder(repository);
        order.verify(repository).findByIdForUpdate(20L);
        order.verify(repository).updateAddress(20L, 1L, request);
    }

    @Test
    void missingCustomerCannotPersistAnAddress() {
        when(repository.findByIdForUpdate(20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new UpdateCustomerAddressService(repository, validator)
                .updateCustomerAddress(20L, 1L, address(true)))
                .isInstanceOf(CustomerNotFoundException.class);

        verify(repository, never()).updateAddress(anyLong(), anyLong(), any());
    }

    private CustomerAddress address(boolean fiscal) {
        return CustomerAddress.builder().address("DIRECCION").ubigeo("150101")
                .department("LIMA").province("LIMA").district("LIMA").fiscal(fiscal).build();
    }
}
