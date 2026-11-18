package com.paulfernandosr.possystembackend.customer.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.customer.domain.Customer;
import com.paulfernandosr.possystembackend.customer.domain.CustomerAddress;
import com.paulfernandosr.possystembackend.customer.domain.DocumentType;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.KeyHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class PostgresCustomerRepositoryTest {
    private final JdbcClient jdbc = mock(JdbcClient.class);
    private final PostgresCustomerRepository repository = spy(new PostgresCustomerRepository(jdbc));

    @Test
    void fullSnapshotLocksParentThenAddressesInStableOrderBeforeReading() {
        var parent = statement();
        var addresses = statement();
        JdbcClient.MappedQuerySpec<Long> parentRows = mock(JdbcClient.MappedQuerySpec.class);
        JdbcClient.MappedQuerySpec<Long> addressRows = mock(JdbcClient.MappedQuerySpec.class);
        when(jdbc.sql("SELECT id FROM customers WHERE id = ? FOR UPDATE")).thenReturn(parent);
        when(parent.query(Long.class)).thenReturn(parentRows);
        when(parentRows.optional()).thenReturn(Optional.of(20L));
        when(jdbc.sql("SELECT id FROM customer_address WHERE customer_id = ? ORDER BY id FOR UPDATE"))
                .thenReturn(addresses);
        when(addresses.query(Long.class)).thenReturn(addressRows);
        when(addressRows.list()).thenReturn(List.of(1L, 2L));
        Customer snapshot = Customer.builder().id(20L).build();
        doReturn(Optional.of(snapshot)).when(repository).findById(20L);

        assertThat(repository.findByIdForUpdate(20L)).containsSame(snapshot);

        var order = inOrder(parentRows, addressRows, repository);
        order.verify(parentRows).optional();
        order.verify(addressRows).list();
        order.verify(repository).findById(20L);
        verify(parent).param(20L);
        verify(addresses).param(20L);
    }

    @Test
    void absentParentDoesNotReadOrLockAddresses() {
        var parent = statement();
        JdbcClient.MappedQuerySpec<Long> rows = mock(JdbcClient.MappedQuerySpec.class);
        when(jdbc.sql("SELECT id FROM customers WHERE id = ? FOR UPDATE")).thenReturn(parent);
        when(parent.query(Long.class)).thenReturn(rows);
        when(rows.optional()).thenReturn(Optional.empty());

        assertThat(repository.findByIdForUpdate(20L)).isEmpty();

        verify(jdbc, never()).sql(contains("customer_address"));
        verify(repository, never()).findById(anyLong());
    }

    @Test
    void changingFiscalAddressSynchronizesMainAddressAfterTheAddressIsSaved() {
        var lock = statement();
        var demote = statement();
        var update = statement();
        var sync = statement();
        JdbcClient.MappedQuerySpec<CustomerAddress> currentRows = mock(JdbcClient.MappedQuerySpec.class);
        JdbcClient.MappedQuerySpec<CustomerAddress> savedRows = mock(JdbcClient.MappedQuerySpec.class);
        when(jdbc.sql(contains("FOR UPDATE"))).thenReturn(lock);
        when(lock.query(CustomerAddress.class)).thenReturn(currentRows);
        when(currentRows.optional()).thenReturn(Optional.of(address(true)));
        when(jdbc.sql(contains("SET fiscal = FALSE"))).thenReturn(demote);
        when(jdbc.sql(contains("SET address = ?"))).thenReturn(update);
        when(update.query(CustomerAddress.class)).thenReturn(savedRows);
        CustomerAddress saved = address(true);
        when(savedRows.optional()).thenReturn(Optional.of(saved));
        when(jdbc.sql(contains("UPDATE customers c"))).thenReturn(sync);

        assertThat(repository.updateAddress(20L, 1L, address(true))).isSameAs(saved);

        var order = inOrder(demote, savedRows, sync);
        order.verify(demote).update();
        order.verify(savedRows).optional();
        order.verify(sync).param(20L);
        order.verify(sync).update();
        verify(jdbc).sql(argThat(sql -> sql.contains("UPDATE customers c")
                && sql.contains("address = ca.address") && sql.contains("ubigeo = ca.ubigeo")
                && sql.contains("department = ca.department") && sql.contains("province = ca.province")
                && sql.contains("district = ca.district") && sql.contains("ca.fiscal = TRUE")
                && sql.contains("ca.enabled = TRUE")));
    }

    @Test
    void editingAnAnnexDoesNotReplaceTheFiscalHeader() {
        var lock = statement();
        var update = statement();
        JdbcClient.MappedQuerySpec<CustomerAddress> currentRows = mock(JdbcClient.MappedQuerySpec.class);
        JdbcClient.MappedQuerySpec<CustomerAddress> savedRows = mock(JdbcClient.MappedQuerySpec.class);
        when(jdbc.sql(contains("FOR UPDATE"))).thenReturn(lock);
        when(lock.query(CustomerAddress.class)).thenReturn(currentRows);
        when(currentRows.optional()).thenReturn(Optional.of(address(false)));
        when(jdbc.sql(contains("SET address = ?"))).thenReturn(update);
        when(update.query(CustomerAddress.class)).thenReturn(savedRows);
        when(savedRows.optional()).thenReturn(Optional.of(address(false)));

        repository.updateAddress(20L, 1L, address(false));

        verify(jdbc, never()).sql(contains("UPDATE customers c"));
        verify(jdbc, never()).sql(contains("SET fiscal = FALSE"));
    }

    @Test
    void creatingTheFiscalAddressSynchronizesTheHeader() {
        var insert = statement();
        var sync = statement();
        when(jdbc.sql(contains("INSERT INTO customer_address"))).thenReturn(insert);
        when(jdbc.sql(contains("UPDATE customers c"))).thenReturn(sync);
        doAnswer(invocation -> {
            KeyHolder holder = invocation.getArgument(0);
            holder.getKeyList().add(Map.of("id", 1L));
            return 1;
        }).when(insert).update(any(KeyHolder.class), eq("id"));

        CustomerAddress saved = repository.createAddress(20L, address(true));

        assertThat(saved.getId()).isEqualTo(1L);
        var order = inOrder(insert, sync);
        order.verify(insert).update(any(KeyHolder.class), eq("id"));
        order.verify(sync).param(20L);
        order.verify(sync).update();
    }

    @Test
    void customerInsertPersistsRootContactWhenThereIsNoAddress() {
        var insert = statement();
        var addresses = statement();
        JdbcClient.MappedQuerySpec<CustomerAddress> addressRows = mock(JdbcClient.MappedQuerySpec.class);
        when(jdbc.sql(contains("INSERT INTO customers"))).thenReturn(insert);
        when(jdbc.sql(contains("FROM customer_address"))).thenReturn(addresses);
        when(addresses.query(CustomerAddress.class)).thenReturn(addressRows);
        when(addressRows.list()).thenReturn(List.of());
        doAnswer(invocation -> {
            KeyHolder holder = invocation.getArgument(0);
            holder.getKeyList().add(Map.of("id", 20L));
            return 1;
        }).when(insert).update(any(KeyHolder.class), eq("id"));
        Customer customer = Customer.builder().documentType(DocumentType.RUC)
                .documentNumber("20123456789").legalName("CLIENTE")
                .phone("999123456").email("cliente@example.com").build();

        repository.create(customer);

        var values = org.mockito.ArgumentCaptor.forClass(Object[].class);
        verify(insert).params(values.capture());
        assertThat(values.getValue()[3]).isEqualTo("999123456");
        assertThat(values.getValue()[4]).isEqualTo("cliente@example.com");
        assertThat(customer.getId()).isEqualTo(20L);
        assertThat(customer.getEditRevision()).hasSize(64);
    }

    private JdbcClient.StatementSpec statement() {
        return mock(JdbcClient.StatementSpec.class, RETURNS_SELF);
    }

    private CustomerAddress address(boolean fiscal) {
        return CustomerAddress.builder().id(1L).customerId(20L).address("NUEVA DIRECCION")
                .ubigeo("150101").department("LIMA").province("LIMA").district("LIMA")
                .fiscal(fiscal).enabled(true).build();
    }
}
