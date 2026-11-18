package com.paulfernandosr.possystembackend.customer.application;

import com.paulfernandosr.possystembackend.customer.domain.Customer;
import com.paulfernandosr.possystembackend.customer.domain.CustomerAddress;
import com.paulfernandosr.possystembackend.customer.domain.DocumentType;
import com.paulfernandosr.possystembackend.customer.domain.exception.InvalidCustomerException;
import com.paulfernandosr.possystembackend.customer.domain.port.output.CustomerRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class CreateNewCustomerContactTest {
    private final CustomerRepository repository = mock(CustomerRepository.class);
    private final CreateNewCustomerService service = new CreateNewCustomerService(
            repository, new CustomerAddressContactValidator());

    @Test
    void keepsNormalizedContactWhenAnAddressIsNotProvided() {
        Customer customer = customer();
        customer.setPhone("+51 999 123 456");
        customer.setEmail(" Cliente@Example.COM ");
        customer.setAddresses(List.of());

        Customer created = service.createNewCustomer(customer);

        assertThat(created.getPhone()).isEqualTo("999123456");
        assertThat(created.getEmail()).isEqualTo("cliente@example.com");
        verify(repository).create(customer);
    }

    @Test
    void storesContactOnTheFiscalAddressWhenThereIsAnAddress() {
        Customer customer = customer();
        customer.setPhone("999123456");
        customer.setEmail("cliente@example.com");
        customer.setAddresses(List.of(CustomerAddress.builder()
                .address("Av. Peru 100").ubigeo("150101")
                .department("Lima").province("Lima").district("Lima")
                .fiscal(true).enabled(true).build()));

        Customer created = service.createNewCustomer(customer);

        assertThat(created.getPhone()).isNull();
        assertThat(created.getEmail()).isNull();
        assertThat(created.getAddresses().getFirst().getPhone()).isEqualTo("999123456");
        assertThat(created.getAddresses().getFirst().getEmail()).isEqualTo("cliente@example.com");
    }

    @Test
    void rejectsInvalidContactBeforeCreatingACustomerWithoutAnAddress() {
        Customer customer = customer();
        customer.setPhone("123");

        assertThatThrownBy(() -> service.createNewCustomer(customer))
                .isInstanceOf(InvalidCustomerException.class);
        verify(repository, never()).create(any());
    }

    @Test
    void permitsAnEmptyContactAndNoAddress() {
        Customer created = service.createNewCustomer(customer());
        assertThat(created.getPhone()).isNull();
        assertThat(created.getEmail()).isNull();
        verify(repository).create(created);
    }

    private Customer customer() {
        return Customer.builder().documentType(DocumentType.RUC)
                .documentNumber("20123456789").legalName("Cliente de prueba")
                .enabled(true).build();
    }
}
