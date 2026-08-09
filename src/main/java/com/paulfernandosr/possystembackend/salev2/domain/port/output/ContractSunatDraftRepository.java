package com.paulfernandosr.possystembackend.salev2.domain.port.output;

import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.ContractSunatDraftResponse;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.ContractSunatDraftSaveRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface ContractSunatDraftRepository {

    ContractBase findContractBase(Long contractId);

    ContractSunatDraftResponse findByContractId(Long contractId);

    ContractSunatDraftResponse createFromContract(Long contractId, Long userId);

    ContractSunatDraftResponse saveDraft(Long contractId, ContractSunatDraftSaveRequest request, Long userId);

    ContractSunatDraftResponse lockDraftForEmission(Long contractId);

    void setDraftNumberAndStatus(Long draftId, Long number, String status);

    void markDraftEmissionResult(Long draftId,
                                 Long saleId,
                                 String sunatStatus,
                                 String sunatCode,
                                 String sunatDescription,
                                 String hashCode,
                                 String xmlPath,
                                 String cdrPath,
                                 String pdfPath,
                                 LocalDateTime emittedAt,
                                 String draftStatus);

    List<DraftItemForSunat> findDraftItemsForSunat(Long draftId);

    Long createSaleFromDraft(Long draftId,
                             Long userId,
                             Long number,
                             String notes);

    Long createSaleItemFromDraftLine(Long saleId,
                                      DraftItemForSunat item,
                                      BigDecimal unitCostSnapshot,
                                      BigDecimal totalCostSnapshot);

    void updateSaleTotalsFromDraft(Long saleId, Long draftId);

    void linkDraftToSale(Long draftId, Long saleId);

    void linkContractToSale(Long contractId, Long saleId);

    void markSaleEmissionResult(
            Long saleId,
            String sunatStatus,
            String sunatCode,
            String sunatDescription,
            String hashCode,
            String xmlPath,
            String cdrPath,
            String pdfPath,
            LocalDateTime emittedAt
    );

    @lombok.Getter
    @lombok.Setter
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @lombok.Builder
    class ContractBase {
        private Long contractId;
        private Long saleId;
        private String contractStatus;
        private String paymentType;
        private LocalDate issueDate;
        private String docType;
        private String series;
        private String currency;
        private BigDecimal exchangeRate;
        private String priceList;
        private Long customerId;
        private String customerDocType;
        private String customerDocNumber;
        private String customerName;
        private String customerAddress;
        private String customerUbigeo;
        private String customerDepartment;
        private String customerProvince;
        private String customerDistrict;
        private BigDecimal cashPrice;
    }

    @lombok.Getter
    @lombok.Setter
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @lombok.Builder
    class DraftItemForSunat {
        private Long draftItemId;
        private Integer lineNumber;
        private Long productId;
        private String sku;
        private String description;
        private String presentation;
        private BigDecimal factor;
        private String productCategory;
        private String sunatProductCode;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private BigDecimal revenueTotal;
        private Boolean facturableSunat;
        private Boolean affectsStock;
        private Boolean manageBySerial;
        private String lineKind;
        private Boolean visibleInDocument;

        private Long serialUnitId;
        private String vin;
        private String chassisNumber;
        private String engineNumber;
        private String color;
        private Integer yearMake;
        private String duaNumber;
        private Integer duaItem;

        private String brand;
        private String model;

        private String vehicleType;
        private String bodywork;
        private String engineCapacity;
        private String fuel;
        private BigDecimal cylinders;
        private BigDecimal netWeight;
        private BigDecimal payload;
        private BigDecimal grossWeight;
        private String vehicleClass;
        private String enginePower;
        private String rollingForm;
        private Integer seats;
        private Integer passengers;
        private Integer axles;
        private Integer wheels;
        private BigDecimal length;
        private BigDecimal width;
        private BigDecimal height;
    }
}
