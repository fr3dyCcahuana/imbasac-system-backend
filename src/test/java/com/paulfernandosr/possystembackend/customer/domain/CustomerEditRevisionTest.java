package com.paulfernandosr.possystembackend.customer.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerEditRevisionTest {
    @Test
    void addressQueryOrderAndReturnedRevisionDoNotChangeTheSnapshot() {
        CustomerAddress first = address(1L);
        CustomerAddress second = address(2L);
        Customer customer = customer();
        customer.setAddresses(List.of(first, second));
        String revision = CustomerEditRevision.calculate(customer);

        customer.setEditRevision("a previous returned token");
        customer.setAddresses(List.of(second, first));

        assertThat(CustomerEditRevision.calculate(customer)).isEqualTo(revision);
    }

    @Test
    void changingContactOrAddingRemovingAnAddressChangesTheSnapshot() {
        Customer customer = customer();
        CustomerAddress first = address(1L);
        customer.setAddresses(List.of(first));
        String initial = CustomerEditRevision.calculate(customer);
        first.setPhone("999123456");
        assertThat(CustomerEditRevision.calculate(customer)).isNotEqualTo(initial);
        first.setPhone(null);
        customer.setAddresses(List.of(first, address(2L)));
        assertThat(CustomerEditRevision.calculate(customer)).isNotEqualTo(initial);
        customer.setAddresses(List.of());
        assertThat(CustomerEditRevision.calculate(customer)).isNotEqualTo(initial);
    }

    @Test
    void headerAndGeolocationChangesAreIncluded() {
        Customer customer = customer();
        CustomerAddress address = address(1L);
        customer.setAddresses(List.of(address));
        String initial = CustomerEditRevision.calculate(customer);
        customer.setLegalName("OTRA RAZON SOCIAL");
        assertThat(CustomerEditRevision.calculate(customer)).isNotEqualTo(initial);
        customer.setLegalName("CLIENTE");
        address.setUpdatedAt(LocalDateTime.of(2026, 10, 29, 10, 0));
        assertThat(CustomerEditRevision.calculate(customer)).isNotEqualTo(initial);
    }

    @Test
    void nullAndEmptyContactHaveDifferentRevisions() {
        Customer customer = customer();
        String initial = CustomerEditRevision.calculate(customer);
        customer.setPhone("");
        assertThat(CustomerEditRevision.calculate(customer)).isNotEqualTo(initial);
    }

    private Customer customer() {
        return Customer.builder().id(20L).documentType(DocumentType.RUC)
                .documentNumber("20123456789").legalName("CLIENTE").enabled(true).build();
    }

    private CustomerAddress address(Long id) {
        return CustomerAddress.builder().id(id).customerId(20L).address("DIRECCION " + id)
                .ubigeo("150101").department("LIMA").province("LIMA").district("LIMA")
                .enabled(true).fiscal(id == 1L).position(id.intValue()).build();
    }
}
