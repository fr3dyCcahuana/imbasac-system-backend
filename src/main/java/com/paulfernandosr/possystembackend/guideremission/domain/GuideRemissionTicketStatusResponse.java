package com.paulfernandosr.possystembackend.guideremission.domain;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GuideRemissionTicketStatusResponse {
    private String ticket;
    private String ticketRpta;
    private String indCdrGenerado;
    private String cdrHash;
    private String cdrMsjSunat;
    private String cdrResponseCode;
    private String documentDescription;
    private String rutaXml;
    private String rutaCdr;
    private String numerror;
    private Integer cod;
    private String msg;
    private String exc;
}
