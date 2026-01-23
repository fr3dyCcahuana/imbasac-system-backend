package com.paulfernandosr.possystembackend.customer.infrastructure.adapter.output.apisnet;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JuridicalPerson {
    @JsonProperty("razonSocial")
    private String businessName;
    @JsonProperty("tipoDocumento")
    private String documentType;
    @JsonProperty("numeroDocumento")
    private String documentNumber;
    @JsonProperty("estado")
    private String status;
    @JsonProperty("condicion")
    private String condition;
    @JsonProperty("direccion")
    private String address;
    @JsonProperty("ubigeo")
    private String ubigeo;
    @JsonProperty("viaTipo")
    private String streetType;
    @JsonProperty("viaNombre")
    private String streetName;
    @JsonProperty("zonaCodigo")
    private String zoneCode;
    @JsonProperty("zonaTipo")
    private String zoneType;
    @JsonProperty("numero")
    private String number;
    @JsonProperty("interior")
    private String interior;
    @JsonProperty("lote")
    private String lot;
    @JsonProperty("dpto")
    private String apartment;
    @JsonProperty("manzana")
    private String block;
    @JsonProperty("kilometro")
    private String kilometer;
    @JsonProperty("distrito")
    private String district;
    @JsonProperty("provincia")
    private String province;
    @JsonProperty("departamento")
    private String department;

    @JsonProperty("EsAgenteRetencion")
    private boolean isRetentionAgent;

    @JsonProperty("EsBuenContribuyente")
    private boolean isGoodContributor;

    @JsonProperty("localesAnexos")
    private List<JuridicalPersonLocal> annexLocations;

    @JsonProperty("tipo")
    private String type;
    @JsonProperty("actividadEconomica")
    private String economicActivity;
    @JsonProperty("numeroTrabajadores")
    private String numberOfEmployees;
    @JsonProperty("tipoFacturacion")
    private String billingType;
    @JsonProperty("tipoContabilidad")
    private String accountingType;
    @JsonProperty("comercioExterior")
    private String foreignTrade;
}
