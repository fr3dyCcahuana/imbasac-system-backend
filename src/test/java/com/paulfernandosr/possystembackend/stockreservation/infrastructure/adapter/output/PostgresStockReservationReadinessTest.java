package com.paulfernandosr.possystembackend.stockreservation.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.stockreservation.domain.exception.StockReservationException;
import com.paulfernandosr.possystembackend.stockreservation.domain.model.StockReservationReassignment;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class PostgresStockReservationReadinessTest {
    @Test
    void missingAuditTableStopsBeforeLockingOrMutatingReservations() {
        JdbcClient jdbc = mock(JdbcClient.class);
        JdbcClient.StatementSpec statement = mock(JdbcClient.StatementSpec.class);
        @SuppressWarnings("unchecked")
        JdbcClient.MappedQuerySpec<Boolean> result = mock(JdbcClient.MappedQuerySpec.class);
        when(jdbc.sql(contains("to_regclass"))).thenReturn(statement);
        when(statement.query(Boolean.class)).thenReturn(result);
        when(result.single()).thenReturn(false);

        assertThatThrownBy(() -> new PostgresProductStockReservationRepository(jdbc).prepareStockForSale(
                Map.of(1L, BigDecimal.valueOf(5)), 1L,
                List.of(new StockReservationReassignment(2L, BigDecimal.valueOf(3))), 900L, 77L))
                .isInstanceOf(StockReservationException.class)
                .hasMessage("No se puede utilizar stock reservado porque falta completar una actualización del sistema. "
                        + "Contacta al administrador y vuelve a intentar cuando esté aplicada.");

        verify(jdbc).sql(contains("to_regclass"));
        verifyNoMoreInteractions(jdbc);
    }

    @Test
    void unrelatedDatabaseFailureIsNotMisreportedAsMissingMigration() {
        JdbcClient jdbc = mock(JdbcClient.class);
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("connection lost");
        when(jdbc.sql(contains("to_regclass"))).thenThrow(failure);

        assertThatThrownBy(() -> new PostgresProductStockReservationRepository(jdbc).prepareStockForSale(
                Map.of(1L, BigDecimal.valueOf(5)), 1L,
                List.of(new StockReservationReassignment(2L, BigDecimal.valueOf(3))), 900L, 77L))
                .isSameAs(failure);
    }

    @Test
    void doesNotProbeAuditSchemaWithoutSelectedReassignments() {
        JdbcClient jdbc = mock(JdbcClient.class);
        new PostgresProductStockReservationRepository(jdbc).prepareStockForSale(
                Map.of(), null, List.of(), 900L, 77L);

        verifyNoInteractions(jdbc);
    }
}
