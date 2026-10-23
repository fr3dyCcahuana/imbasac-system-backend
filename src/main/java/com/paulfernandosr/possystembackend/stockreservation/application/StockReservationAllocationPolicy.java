package com.paulfernandosr.possystembackend.stockreservation.application;

import com.paulfernandosr.possystembackend.stockreservation.domain.exception.StockReservationException;
import com.paulfernandosr.possystembackend.stockreservation.domain.model.StockReservationReassignment;

import java.math.BigDecimal;
import java.util.*;

/** Checks the exact confirmation against the stock snapshot taken under product locks. */
public final class StockReservationAllocationPolicy {
    private StockReservationAllocationPolicy() { }

    public record ReservationState(Long id, Long proformaId, Long productId,
                                   BigDecimal quantity, boolean transferable) { }
    public record Allocation(ReservationState reservation, BigDecimal quantity) { }

    public static Map<Long, BigDecimal> requestedQuantities(List<StockReservationReassignment> requested) {
        Map<Long, BigDecimal> quantities = new TreeMap<>();
        if (requested == null) return quantities;
        for (StockReservationReassignment item : requested) {
            if (item == null || item.getReservationId() == null || item.getReservationId() <= 0
                    || item.getQuantity() == null || item.getQuantity().signum() <= 0
                    || item.getQuantity().stripTrailingZeros().scale() > 3) {
                throw new StockReservationException("Reserva seleccionada inválida: indique una cantidad positiva con hasta 3 decimales.");
            }
            if (quantities.putIfAbsent(item.getReservationId(), item.getQuantity()) != null) {
                throw new StockReservationException("Reserva seleccionada repetida: " + item.getReservationId());
            }
        }
        return quantities;
    }

    public static List<Allocation> allocate(Map<Long, BigDecimal> needs, Map<Long, BigDecimal> onHand,
                                            List<ReservationState> activeReservations, Long sourceProformaId,
                                            Map<Long, BigDecimal> selected) {
        Map<Long, ReservationState> reservations = new HashMap<>();
        Map<Long, BigDecimal> reservedByOthers = new HashMap<>();
        for (ReservationState reservation : activeReservations) {
            reservations.put(reservation.id(), reservation);
            if (!Objects.equals(reservation.proformaId(), sourceProformaId)) {
                reservedByOthers.merge(reservation.productId(), reservation.quantity(), BigDecimal::add);
            }
        }

        List<Allocation> allocations = new ArrayList<>();
        Map<Long, BigDecimal> selectedByProduct = new HashMap<>();
        for (Map.Entry<Long, BigDecimal> selection : selected.entrySet()) {
            ReservationState reservation = reservations.get(selection.getKey());
            if (reservation == null || !needs.containsKey(reservation.productId())) {
                throw new StockReservationException("La reserva " + selection.getKey()
                        + " ya no está vigente o no corresponde a los productos de esta venta. Actualice las reservas.");
            }
            if (!reservation.transferable() || Objects.equals(reservation.proformaId(), sourceProformaId)) {
                throw new StockReservationException("La reserva " + selection.getKey()
                        + " no se puede reasignar: debe ser de otra proforma y de un producto con stock sin serie/VIN.");
            }
            if (selection.getValue().compareTo(reservation.quantity()) > 0) {
                throw new StockReservationException("La cantidad de la reserva " + selection.getKey()
                        + " cambió. Actualice las reservas antes de confirmar.");
            }
            selectedByProduct.merge(reservation.productId(), selection.getValue(), BigDecimal::add);
            allocations.add(new Allocation(reservation, selection.getValue()));
        }

        for (Map.Entry<Long, BigDecimal> need : new TreeMap<>(needs).entrySet()) {
            BigDecimal physical = onHand.getOrDefault(need.getKey(), BigDecimal.ZERO);
            if (physical.compareTo(need.getValue()) < 0) {
                throw new StockReservationException("Stock físico insuficiente para productId=" + need.getKey()
                        + ". Solicitado=" + need.getValue() + ", existencia=" + physical
                        + ", faltante=" + need.getValue().subtract(physical) + ". El vendedor debe resolver el faltante.");
            }
            // Own reservations are already usable. Free stock is used before taking donor reservations.
            BigDecimal available = physical.subtract(reservedByOthers.getOrDefault(need.getKey(), BigDecimal.ZERO));
            BigDecimal shortage = need.getValue().subtract(available).max(BigDecimal.ZERO);
            BigDecimal confirmed = selectedByProduct.getOrDefault(need.getKey(), BigDecimal.ZERO);
            if (confirmed.compareTo(shortage) != 0) {
                throw new StockReservationException("Las reservas a utilizar cambiaron para productId=" + need.getKey()
                        + ". Cantidad necesaria de otras proformas=" + shortage + ", confirmada=" + confirmed
                        + ". Actualice las reservas y confirme únicamente la cantidad necesaria.");
            }
        }
        return allocations;
    }
}
