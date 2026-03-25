package com.paulfernandosr.possystembackend.guideremission.domain;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuideRemissionDocument {
    private Long id;
    private String companyRuc;
    private String serie;
    private String numero;
    private LocalDate issueDate;
    private LocalTime issueTime;
    private LocalDate transferDate;
    private String transferReasonCode;
    private String transferModeCode;
    private String relatedDocumentTypeCode;
    private String relatedDocumentSerie;
    private String relatedDocumentNumero;
    private String transporterDocumentNumber;
    private String transporterName;
    private String legacyTransportEntityId;
    private String legacyTransportMtcNumber;
    private String driverDni;
    private String driverFullName;
    private String driverLicense;
    private String vehiclePlate;
    private Integer recipientDocumentType;
    private String recipientDocumentNumber;
    private String recipientName;
    private String departureUbigeo;
    private String departureAddress;
    private String departureEstablishmentCode;
    private String arrivalUbigeo;
    private String arrivalAddress;
    private String arrivalEstablishmentCode;
    private BigDecimal totalWeight;
    private String numberOfPackages;
    private String notes;
    private String ticket;
    private String status;
    private String ticketResponseCode;
    private String cdrGenerated;
    private String cdrHash;
    private String cdrMessage;
    private String documentDescription;
    private OffsetDateTime submittedAt;
    @Builder.Default
    private List<GuideRemissionDocumentItem> items = new ArrayList<>();
}
