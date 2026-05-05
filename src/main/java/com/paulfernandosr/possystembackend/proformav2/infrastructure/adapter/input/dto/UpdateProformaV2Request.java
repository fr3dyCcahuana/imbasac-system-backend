package com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto;

import com.paulfernandosr.possystembackend.salev2.domain.model.PaymentType;
import com.paulfernandosr.possystembackend.salev2.domain.model.TaxStatus;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateProformaV2Request {

    /**
     * Opcional. Si no se envía, se conserva la fecha actual de la proforma.
     * Formato esperado: yyyy-MM-dd.
     */
    private String issueDate;

    /**
     * Opcional. Si no se envía, se conserva la lista actual.
     * Valores válidos: A/B/C/D.
     */
    private Character priceList;

    /**
     * Opcional. Si no se envía, se conserva la moneda actual.
     */
    private String currency;

    /**
     * Opcional. Si no se envía, se conserva el tratamiento tributario actual.
     */
    private TaxStatus taxStatus;

    /**
     * Opcional. Si no se envía, se conserva la tasa actual o 18.00 si no existe.
     */
    private BigDecimal igvRate;

    /**
     * Solo válido cuando taxStatus=GRAVADA.
     */
    private Boolean igvIncluded;

    private Long customerId;
    private String customerDocType;
    private String customerDocNumber;
    private String customerName;
    private String customerAddress;

    private PaymentType paymentType;
    private Integer creditDays;
    private String dueDate;

    private String notes;

    /**
     * Lista final de productos que debe quedar en la proforma.
     * Para agregar/quitar desde el modal, el frontend envía la lista completa resultante.
     */
    private List<Item> items;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Item {
        /**
         * Opcional e informativo para el frontend. El backend recalcula líneas e inserta nuevamente los ítems.
         */
        private Long id;

        private Long productId;
        private String description;
        private String presentation;
        private String factor;
        private String quantity;
        private String discountPercent;

        /**
         * Precio unitario editado desde frontend.
         * Si no se envía, se toma el precio según priceList A/B/C/D.
         */
        private BigDecimal unitPriceOverride;
    }
}
