package com.paulfernandosr.possystembackend.customer.infrastructure.adapter.output.apisnet;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NaturalPerson {
    @JsonProperty("nombres")
    private String givenNames;
    @JsonProperty("apellidoPaterno")
    private String lastName;
    @JsonProperty("apellidoMaterno")
    private String secondLastName;
    @JsonProperty("tipoDocumento")
    private String documentType;
    @JsonProperty("numeroDocumento")
    private String documentNumber;
    @JsonProperty("digitoVerificador")
    private String verificationDigit;
}
