package com.paulfernandosr.possystembackend.customer.infrastructure.adapter.output.apisnet;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JuridicalPersonLocal {

    @JsonProperty("direccion")
    private String address;

    @JsonProperty("ubigeo")
    private String ubigeo;

    @JsonProperty("departamento")
    private String department;

    @JsonProperty("provincia")
    private String province;

    @JsonProperty("distrito")
    private String district;
}
