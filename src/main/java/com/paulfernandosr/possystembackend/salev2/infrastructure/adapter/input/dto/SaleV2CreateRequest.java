package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto;

import com.paulfernandosr.possystembackend.salev2.domain.model.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleV2CreateRequest {

    private Long stationId;
    private Long saleSessionId;

    private DocType docType;          // SIMPLE/BOLETA/FACTURA
    private String series;            // serie configurada en app.document-series y document_series
    private LocalDate issueDate;

    private String currency;          // PEN
    private BigDecimal exchangeRate;

    private PriceList priceList;      // A/B/C/D

    private Long customerId;
    private String customerDocType;
    private String customerDocNumber;
    private String customerName;
    private String customerAddress;

    private TaxStatus taxStatus;      // GRAVADA/NO_GRAVADA
    private String taxReason;
    private BigDecimal igvRate;       // 18.00

    /**
     * NUEVO:
     * true  => unitPrice incluye IGV (solo válido si taxStatus=GRAVADA)
     * false => unitPrice NO incluye IGV
     */
    private Boolean igvIncluded;

    private PaymentType paymentType;  // CONTADO/CREDITO
    private Integer creditDays;
    private LocalDate dueDate;

    private String notes;

    /**
     * NÚMERO VISIBLE de la proforma usada por el facturador.
     * Regla actual del negocio: el frontend busca/carga por número de proforma,
     * pero el backend resuelve internamente el ID real de tabla para guardar la relación.
     *
     * Ejemplo: si el usuario carga Proforma Nro 34, enviar sourceProformaNumber=34.
     */
    private Long sourceProformaNumber;

    /**
     * Campo legacy de compatibilidad con el frontend anterior.
     * IMPORTANTE: aunque el nombre diga "Id", CreateSaleV2Service lo interpreta como
     * NÚMERO VISIBLE de proforma cuando sourceProformaNumber no viene informado.
     *
     * No se debe usar este valor como proforma.id para actualizar estado o relación.
     */
    @Deprecated
    private Long sourceProformaId;

    private List<Item> items;
    private Payment payment;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Item {
        private Long productId;
        private BigDecimal quantity;

        /**
         * Descuento solo permitido si docType=SIMPLE.
         * En BOLETA/FACTURA debe ser 0.
         */
        private BigDecimal discountPercent;

        private LineKind lineKind;        // VENDIDO/OBSEQUIO
        private String giftReason;

        /**
         * NUEVO:
         * Si viene, se usa tal cual (snapshot de proforma / override de precio)
         */
        private BigDecimal unitPriceOverride;

        // Para productos manage_by_serial=true:
        private List<Long> serialUnitIds;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Payment {
        private PaymentMethod method; // EFECTIVO/YAPE/TRANSFERENCIA/OTRO
    }
}
