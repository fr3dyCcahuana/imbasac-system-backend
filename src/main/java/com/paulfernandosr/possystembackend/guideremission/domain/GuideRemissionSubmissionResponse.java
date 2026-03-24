package com.paulfernandosr.possystembackend.guideremission.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class GuideRemissionSubmissionResponse {

    @JsonProperty("numTicket")
    private String numTicket;

    @JsonProperty("fecRecepcion")
    private String fecRecepcion;

    @JsonProperty("cod")
    private Integer cod;

    @JsonProperty("msg")
    private String msg;

    @JsonProperty("exc")
    private String exc;
}
