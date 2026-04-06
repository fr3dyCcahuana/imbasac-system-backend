package com.paulfernandosr.possystembackend.guideremission.domain;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GuideRemissionPageItem {
    private Long id;
    private String serie;
    private String numero;
    private LocalDate issueDate;
    private LocalTime issueTime;
    private LocalDate transferDate;
    private String status;
    private String transferModeCode;
    private String transferModeLabel;
    private String recipientDocumentNumber;
    private String recipientName;
    private String transporterDocumentNumber;
    private String transporterName;
    private String driverDocumentNumber;
    private String driverFullName;
    private String driverLicense;
    private String vehiclePlate;
    private BigDecimal totalWeight;
    private String numberOfPackages;
    private String ticket;
    private String ticketResponseCode;
    private String primaryRelatedDocumentTypeCode;
    private String primaryRelatedDocumentSerie;
    private String primaryRelatedDocumentNumero;
    private Integer relatedDocumentsCount;
    private Integer itemsCount;
    private OffsetDateTime submittedAt;
    private OffsetDateTime createdAt;
}
