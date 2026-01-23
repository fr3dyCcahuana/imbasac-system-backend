package com.paulfernandosr.possystembackend.customer.domain;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Customer {
    private Long id;
    private String legalName;
    private DocumentType documentType;
    private String documentNumber;

    private String address;

    // --- SUNAT / RUC (solo aplica cuando documentType=RUC)
    private String sunatStatus;      // estado
    private String sunatCondition;   // condicion

    // Datos de ubicación fiscal
    private String ubigeo;
    private String department;
    private String province;
    private String district;

    // Desglose de dirección (opcional)
    private String streetType;   // viaTipo
    private String streetName;   // viaNombre
    private String zoneCode;     // zonaCodigo
    private String zoneType;     // zonaTipo
    private String addressNumber;// numero
    private String interior;     // interior
    private String lot;          // lote
    private String apartment;    // dpto
    private String block;        // manzana
    private String kilometer;    // kilometro

    private boolean retentionAgent;  // EsAgenteRetencion
    private boolean goodContributor; // EsBuenContribuyente

    // Datos adicionales SUNAT (opcionales)
    private String sunatType;         // tipo
    private String economicActivity;  // actividadEconomica
    private String numberOfEmployees; // numeroTrabajadores
    private String billingType;       // tipoFacturacion
    private String accountingType;    // tipoContabilidad
    private String foreignTrade;      // comercioExterior

    /**
     * Direcciones del cliente.
     * - Debe existir 1 fiscal (fiscal=true) si se quiere generar guía.
     * - Puede incluir locales anexos (fiscal=false).
     */
    @Builder.Default
    private List<CustomerAddress> addresses = new ArrayList<>();

    private boolean enabled;
}
