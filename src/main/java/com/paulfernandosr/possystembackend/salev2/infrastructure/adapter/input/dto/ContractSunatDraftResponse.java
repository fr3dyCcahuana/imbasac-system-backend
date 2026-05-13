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
public class ContractSunatDraftResponse {

    private Long id;

    private Long saleId;
    private Long contractId;

    private String docType;
    private String series;
    /** NULL hasta emitir SUNAT. */
    private Long number;
    private LocalDate issueDate;

    /** Cliente del comprobante SUNAT. Puede ser distinto al cliente del contrato. */
    private Long billingCustomerId;
    private String customerDocType;
    private String customerDocNumber;
    private String customerName;
    private String customerAddress;
    private String customerUbigeo;
    private String customerDepartment;
    private String customerProvince;
    private String customerDistrict;

    private String paymentMethod;

    private String taxStatus;
    private String taxReason;
    private BigDecimal igvRate;
    private Boolean igvIncluded;

    private BigDecimal subtotal;
    private BigDecimal discountTotal;
    private BigDecimal igvAmount;
    private BigDecimal total;

    private String status;
    private String editReason;

    private String sunatStatus;

    private List<ContractSunatDraftItemResponse> items;
}
