package com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.sunat;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class DocumentResponse {
    @JsonProperty("data")
    private Data data;

    @Getter
    @Setter
    @Builder
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Data {
        @JsonProperty("respuesta_sunat_codigo")
        private String code;
        @JsonProperty("respuesta_sunat_descripcion")
        private String description;
        @JsonProperty("ruta_xml")
        private String xmlPath;
        @JsonProperty("ruta_cdr")
        private String cdrPath;
        @JsonProperty("ruta_pdf")
        private String pdfPath;
        @JsonProperty("xml_base_64")
        private String xmlBase64;
        @JsonProperty("cdr_base_64")
        private String cdrBase64;
        @JsonProperty("codigo_hash")
        private Hash hash;
    }

    @Getter
    @Setter
    @Builder
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Hash {
        @JsonProperty("0")
        private String code;
    }
}
