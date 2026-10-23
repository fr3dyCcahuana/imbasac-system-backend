package com.paulfernandosr.possystembackend.stockreservation.application;

import com.paulfernandosr.possystembackend.stockreservation.application.StockReservationAllocationPolicy.ReservationState;
import com.paulfernandosr.possystembackend.stockreservation.domain.exception.StockReservationException;
import com.paulfernandosr.possystembackend.stockreservation.domain.model.StockReservationReassignment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class StockReservationAllocationPolicyTest {
    private static BigDecimal qty(String value) { return new BigDecimal(value); }

    @Test
    void ownReservationAndFreeStockAreUsedBeforeDonors() {
        var active = List.of(new ReservationState(1L, 10L, 100L, qty("5"), true),
                new ReservationState(2L, 20L, 100L, qty("3"), true));
        assertThat(StockReservationAllocationPolicy.allocate(Map.of(100L, qty("7")), Map.of(100L, qty("10")),
                active, 10L, Map.of())).isEmpty();
    }

    @Test
    void takesOnlyShortageFromChosenDonor() {
        var donor = new ReservationState(2L, 20L, 100L, qty("8"), true);
        var result = StockReservationAllocationPolicy.allocate(Map.of(100L, qty("5")), Map.of(100L, qty("10")),
                List.of(donor), null, Map.of(2L, qty("3")));
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().reservation()).isSameAs(donor);
        assertThat(result.getFirst().quantity()).isEqualByComparingTo("3");
        assertThat(donor.quantity()).isEqualByComparingTo("8");
    }

    @Test
    void acceptsMultipleExplicitDonorsWithExactCombinedShortage() {
        var active = List.of(new ReservationState(2L, 20L, 100L, qty("4"), true),
                new ReservationState(3L, 30L, 100L, qty("3"), true));
        assertThat(StockReservationAllocationPolicy.allocate(Map.of(100L, qty("9")), Map.of(100L, qty("10")),
                active, null, Map.of(2L, qty("4"), 3L, qty("2")))).hasSize(2);
    }

    @Test
    void rejectsDonationsWhenPhysicalStockCannotCoverSale() {
        var donor = new ReservationState(2L, 20L, 100L, qty("3"), true);
        assertThatThrownBy(() -> StockReservationAllocationPolicy.allocate(Map.of(100L, qty("5")),
                Map.of(100L, qty("3")), List.of(donor), null, Map.of(2L, qty("3"))))
                .isInstanceOf(StockReservationException.class).hasMessageContaining("Stock físico insuficiente")
                .hasMessageContaining("faltante=2");
    }

    @Test
    void rejectsMissingConfirmationForReservedStock() {
        var donor = new ReservationState(2L, 20L, 100L, qty("8"), true);
        assertThatThrownBy(() -> StockReservationAllocationPolicy.allocate(Map.of(100L, qty("5")),
                Map.of(100L, qty("10")), List.of(donor), null, Map.of()))
                .isInstanceOf(StockReservationException.class).hasMessageContaining("necesaria de otras proformas=3");
    }

    @Test
    void rejectsExtraReassignmentEvenIfDonorHasEnoughUnits() {
        var donor = new ReservationState(2L, 20L, 100L, qty("8"), true);
        assertThatThrownBy(() -> StockReservationAllocationPolicy.allocate(Map.of(100L, qty("5")),
                Map.of(100L, qty("10")), List.of(donor), null, Map.of(2L, qty("4"))))
                .isInstanceOf(StockReservationException.class).hasMessageContaining("confirmada=4");
    }

    @Test
    void rejectsStaleReservationOrNewlySufficientFreeStock() {
        assertThatThrownBy(() -> StockReservationAllocationPolicy.allocate(Map.of(100L, qty("2")),
                Map.of(100L, qty("5")), List.of(), null, Map.of(2L, qty("2"))))
                .isInstanceOf(StockReservationException.class).hasMessageContaining("ya no está vigente");
        assertThatThrownBy(() -> StockReservationAllocationPolicy.allocate(Map.of(100L, qty("2")),
                Map.of(100L, qty("5")), List.of(new ReservationState(2L, 20L, 100L, qty("2"), true)),
                null, Map.of(2L, qty("2"))))
                .isInstanceOf(StockReservationException.class).hasMessageContaining("necesaria de otras proformas=0");
    }

    @Test
    void rejectsOwnAndNonTransferableSerialReservations() {
        assertThatThrownBy(() -> StockReservationAllocationPolicy.allocate(Map.of(100L, qty("2")),
                Map.of(100L, qty("5")), List.of(new ReservationState(2L, 10L, 100L, qty("2"), true)),
                10L, Map.of(2L, qty("2"))))
                .isInstanceOf(StockReservationException.class).hasMessageContaining("no se puede reasignar");
        assertThatThrownBy(() -> StockReservationAllocationPolicy.allocate(Map.of(100L, qty("2")),
                Map.of(100L, qty("5")), List.of(new ReservationState(2L, 20L, 100L, qty("2"), false)),
                null, Map.of(2L, qty("2"))))
                .isInstanceOf(StockReservationException.class).hasMessageContaining("sin serie/VIN");
    }

    @Test
    void rejectsDuplicatedAndInvalidSelectionsInsteadOfMergingSilently() {
        assertThatThrownBy(() -> StockReservationAllocationPolicy.requestedQuantities(List.of(
                new StockReservationReassignment(2L, qty("1")), new StockReservationReassignment(2L, qty("1")))))
                .isInstanceOf(StockReservationException.class).hasMessageContaining("repetida");
        for (String invalid : List.of("0", "-1", "0.0001")) {
            assertThatThrownBy(() -> StockReservationAllocationPolicy.requestedQuantities(List.of(
                    new StockReservationReassignment(2L, qty(invalid)))))
                    .isInstanceOf(StockReservationException.class).hasMessageContaining("inválida");
        }
    }
}
