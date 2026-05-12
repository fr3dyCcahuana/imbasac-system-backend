package com.paulfernandosr.possystembackend.salev2.domain.port.output;

import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.ContractSunatDraftResponse;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.ContractSunatDraftSaveRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface ContractSunatDraftRepository {

    ContractSaleBase findContractSaleBase(Long saleId);

    ContractSunatDraftResponse findBySaleId(Long saleId);

    ContractSunatDraftResponse createFromSale(Long saleId, Long userId);

    ContractSunatDraftResponse saveDraft(Long saleId, ContractSunatDraftSaveRequest request, Long userId);

    ContractSunatDraftResponse lockDraftForEmission(Long saleId);

    void setDraftNumberAndStatus(Long draftId, Long number, String status);

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

    void markSaleEmissionError(Long saleId, String description, LocalDateTime emittedAt);

    List<DraftItemForSunat> findDraftItemsForSunat(Long draftId);

    @lombok.Getter
    @lombok.Setter
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @lombok.Builder
    class ContractSaleBase {
        private Long saleId;
        private Long contractId;
        private String saleStatus;
        private String saleDocType;
        private String sunatStatus;
        private String draftStatus;
    }

    @lombok.Getter
    @lombok.Setter
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @lombok.Builder
    class DraftItemForSunat {
        private Integer lineNumber;
        private Long productId;
        private String sku;
        private String description;
        private String productCategory;
        private BigDecimal quantity;
        private BigDecimal revenueTotal;
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
        private Integer cylinders;
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
