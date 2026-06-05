package com.paulfernandosr.possystembackend.salev2.domain.port.output;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface SaleV2SunatRepository {

    LockedSunatSale lockSale(Long saleId);

    List<SaleItemForSunat> findItems(Long saleId);

    void updateEmissionResult(Long saleId,
                              String sunatStatus,
                              String sunatCode,
                              String sunatDescription,
                              String hashCode,
                              String xmlPath,
                              String cdrPath,
                              String pdfPath,
                              LocalDateTime emittedAt);

    void markEmissionError(Long saleId, String description, LocalDateTime emittedAt);

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class LockedSunatSale {
        private Long saleId;
        private String status;
        private String docType;
        private String series;
        private Long number;
        private LocalDate issueDate;
        private LocalDateTime createdAt;
        private String currency;
        private String customerDocType;
        private String customerDocNumber;
        private String customerName;
        private String customerAddress;
        private String customerUbigeo;
        private String customerDepartment;
        private String customerProvince;
        private String customerDistrict;
        private String taxStatus;
        private BigDecimal subtotal;
        private BigDecimal discountTotal;
        private BigDecimal igvAmount;
        private BigDecimal total;
        private String paymentType;
        private String notes;
        private String sunatStatus;
        private String sunatResponseCode;
        private String sunatResponseDescription;
        private String sunatHashCode;
        private String sunatXmlPath;
        private String sunatCdrPath;
        private String sunatPdfPath;
        private LocalDateTime sunatSentAt;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class SaleItemForSunat {
        private Integer lineNumber;
        private Long productId;
        private String sku;
        private String description;
        private String productCategory;
        private BigDecimal quantity;
        private BigDecimal revenueTotal;
        private String lineKind;
        private Boolean visibleInDocument;

        // Unidad física vendida/reservada. En tu BD estos datos viven en product_serial_unit.
        private Long serialUnitId;
        private String vin;
        private String chassisNumber;
        private String engineNumber;
        private String color;
        private Integer yearMake;
        private String duaNumber;
        private Integer duaItem;

        // Datos maestros del producto. En tu BD brand/model viven en product.
        private String brand;
        private String model;

        // Ficha técnica. En tu BD estos datos viven en product_vehicle_specs.
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
