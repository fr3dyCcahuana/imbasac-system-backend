package com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.sunat;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.Collection;

@Getter
@Setter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class DocumentRequest {
    @JsonProperty("empresa")
    private Business business;
    @JsonProperty("cliente")
    private Customer customer;
    @JsonProperty("venta")
    private Sale sale;
    @JsonProperty("items")
    private Collection<Item> items;

    @Getter
    @Setter
    @Builder
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Business {
        @JsonProperty("ruc")
        private String ruc;
        @JsonProperty("razon_social")
        private String businessName;
        @JsonProperty("nombre_comercial")
        private String tradeName;
        @JsonProperty("domicilio_fiscal")
        private String taxAddress;
        @JsonProperty("ubigeo")
        private String ubigeo;
        @JsonProperty("urbanizacion")
        private String neighborhood;
        @JsonProperty("distrito")
        private String district;
        @JsonProperty("provincia")
        private String province;
        @JsonProperty("departamento")
        private String department;
        @JsonProperty("modo")
        private String mode;
        @JsonProperty("usu_secundario_produccion_user")
        private String username;
        @JsonProperty("usu_secundario_produccion_password")
        private String password;
    }

    @Getter
    @Setter
    @Builder
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Customer {
        @JsonProperty("razon_social_nombres")
        private String fullName;
        @JsonProperty("numero_documento")
        private String documentNumber;
        @JsonProperty("codigo_tipo_entidad")
        private String entityTypeCode;
        @JsonProperty("cliente_direccion")
        private String address;
    }

    @Getter
    @Setter
    @Builder
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Sale {
        @JsonProperty("serie")
        private String serial;
        @JsonProperty("numero")
        private String number;
        @JsonProperty("fecha_emision")
        private String issueDate;
        @JsonProperty("hora_emision")
        private String issueTime;
        @JsonProperty("fecha_vencimiento")
        private String dueDate;
        @JsonProperty("moneda_id")
        private String currencyId;
        @JsonProperty("forma_pago_id")
        private String paymentMethodId;
        @JsonProperty("total_gravada")
        private String totalTaxed;
        @JsonProperty("total_igv")
        private String totalIgv;
        @JsonProperty("total_exonerada")
        private String totalExempted;
        @JsonProperty("total_inafecta")
        private String totalUnaffected;
        @JsonProperty("descuento_global")
        private String globalDiscount;
        @JsonProperty("tipo_documento_codigo")
        private String documentTypeCode;
        @JsonProperty("nota")
        private String note;
    }

    @Getter
    @Setter
    @Builder
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        @JsonProperty("producto")
        private String product;
        @JsonProperty("cantidad")
        private String quantity;
        @JsonProperty("precio_base")
        private String basePrice;
        @JsonProperty("codigo_sunat")
        private String sunatCode;
        @JsonProperty("codigo_producto")
        private String productCode;
        @JsonProperty("codigo_unidad")
        private String unitCode;
        @JsonProperty("tipo_igv_codigo")
        private String igvTypeCode;
    }
}
