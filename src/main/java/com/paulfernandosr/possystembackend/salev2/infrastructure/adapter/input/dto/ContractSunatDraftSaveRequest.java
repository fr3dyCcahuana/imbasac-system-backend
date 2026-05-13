package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractSunatDraftSaveRequest {

    private String docType;
    private String series;
    private LocalDate issueDate;

    /** Cliente del comprobante SUNAT. Puede ser distinto al cliente del contrato. */
    private Long billingCustomerId;
    private String billingDocType;
    private String billingDocNumber;
    private String billingName;
    private String billingAddress;
    private String billingUbigeo;
    private String billingDepartment;
    private String billingProvince;
    private String billingDistrict;

    /** EFECTIVO / YAPE / TRANSFERENCIA / OTRO. En CONTADO se registra al emitir SUNAT. */
    private String paymentMethod;

    private String taxStatus;
    private String taxReason;
    private BigDecimal igvRate;
    private Boolean igvIncluded;

    private String editReason;

    private List<Item> items;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Item {
        /** Nuevo flujo por contrato: usar lineNumber. */
        private Integer lineNumber;

        /** Compatibilidad con flujo antiguo por saleId. */
        private Long saleItemId;

        private BigDecimal sunatUnitPrice;
    }
}
