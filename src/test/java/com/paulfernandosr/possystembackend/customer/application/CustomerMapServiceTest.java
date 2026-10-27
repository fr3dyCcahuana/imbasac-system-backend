package com.paulfernandosr.possystembackend.customer.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paulfernandosr.possystembackend.customer.domain.CustomerAddress;
import com.paulfernandosr.possystembackend.customer.domain.GeolocationSource;
import com.paulfernandosr.possystembackend.customer.domain.exception.InvalidCustomerException;
import com.paulfernandosr.possystembackend.customer.infrastructure.adapter.input.dto.CustomerMapDtos.CustomerAddressGeolocationDetailResponse;
import com.paulfernandosr.possystembackend.customer.infrastructure.adapter.input.dto.CustomerMapDtos.CustomerAddressGeolocationUpdateRequest;
import com.paulfernandosr.possystembackend.customer.infrastructure.config.CustomerMapProperties;
import com.paulfernandosr.possystembackend.user.domain.User;
import com.paulfernandosr.possystembackend.user.domain.port.output.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CustomerMapServiceTest {
    private final JdbcClient jdbc = mock(JdbcClient.class);
    private final UserRepository users = mock(UserRepository.class);
    private final CustomerAddressHashService hashes = mock(CustomerAddressHashService.class);
    private final LocalDateTime version = LocalDateTime.of(2026, 10, 24, 10, 0);
    private final Principal actor = () -> "operator";
    private CustomerMapService service;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(9L);
        when(users.findByUsername("operator")).thenReturn(Optional.of(user));
        service = spy(new CustomerMapService(jdbc, users, hashes, new CustomerMapProperties(), new ObjectMapper()));
    }

    @ParameterizedTest
    @CsvSource({"NaN,-77", "Infinity,-77", "-12,NaN", "-12,-Infinity", "-33,-70", "0,0", "91,-77"})
    void invalidCoordinatesNeverReachTheDatabase(double latitude, double longitude) {
        var request = new CustomerAddressGeolocationUpdateRequest(latitude, longitude, null, GeolocationSource.MANUAL, version);
        assertThatThrownBy(() -> service.update(20L, 1L, request, actor, true)).isInstanceOf(InvalidCustomerException.class);
        verifyNoInteractions(jdbc);
    }

    @Test
    void missingCoordinatesNeverReachTheDatabase() {
        assertThatThrownBy(() -> service.update(20L, 1L, null, actor, true)).isInstanceOf(InvalidCustomerException.class);
        verifyNoInteractions(jdbc);
    }

    @Test
    void invalidAccuracyNeverReachesTheDatabase() {
        for (double accuracy : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY}) {
            var request = new CustomerAddressGeolocationUpdateRequest(-12D, -77D, accuracy, GeolocationSource.GPS, version);
            assertThatThrownBy(() -> service.update(20L, 1L, request, actor, true)).isInstanceOf(InvalidCustomerException.class);
        }
        verifyNoInteractions(jdbc);
    }

    @Test
    @SuppressWarnings("unchecked")
    void staleVersionCannotOverwriteAnotherOperatorsLocation() {
        var lock = mock(JdbcClient.StatementSpec.class, RETURNS_SELF);
        JdbcClient.MappedQuerySpec<CustomerAddress> row = mock(JdbcClient.MappedQuerySpec.class);
        when(jdbc.sql(contains("FOR UPDATE"))).thenReturn(lock);
        when(lock.query(CustomerAddress.class)).thenReturn(row);
        when(row.optional()).thenReturn(Optional.of(CustomerAddress.builder().updatedAt(version.plusSeconds(1)).build()));
        var request = new CustomerAddressGeolocationUpdateRequest(-12D, -77D, null, GeolocationSource.MANUAL, version);
        assertThatThrownBy(() -> service.update(20L, 1L, request, actor, true))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(jdbc, never()).sql(contains("UPDATE customer_address"));
        verifyNoInteractions(hashes);
    }

    @Test
    @SuppressWarnings("unchecked")
    void savingCurrentVersionUpdatesOnlyTheChosenCustomerAddress() {
        var lock = mock(JdbcClient.StatementSpec.class, RETURNS_SELF);
        var update = mock(JdbcClient.StatementSpec.class, RETURNS_SELF);
        JdbcClient.MappedQuerySpec<CustomerAddress> row = mock(JdbcClient.MappedQuerySpec.class);
        var address = CustomerAddress.builder().id(1L).customerId(20L).updatedAt(version).build();
        when(jdbc.sql(contains("FOR UPDATE"))).thenReturn(lock);
        when(lock.query(CustomerAddress.class)).thenReturn(row);
        when(row.optional()).thenReturn(Optional.of(address));
        when(jdbc.sql(contains("UPDATE customer_address"))).thenReturn(update);
        when(update.update()).thenReturn(1);
        when(hashes.calculate(address)).thenReturn("address-hash");
        var saved = CustomerAddressGeolocationDetailResponse.builder().addressId(1L).customerId(20L).build();
        doReturn(saved).when(service).detail(20L, 1L);
        var request = new CustomerAddressGeolocationUpdateRequest(-12D, -77D, null, GeolocationSource.MANUAL, version);
        assertThat(service.update(20L, 1L, request, actor, true)).isSameAs(saved);
        verify(lock).params(1L, 20L);
        verify(update).params(-12D, -77D, "VERIFIED", "MANUAL", null, 9L, true, true, 9L, "address-hash", 1L, 20L);
    }
}
