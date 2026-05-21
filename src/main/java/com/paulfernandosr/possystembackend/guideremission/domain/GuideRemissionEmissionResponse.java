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
public class GuideRemissionEmissionResponse {
    private boolean success;
    private String status;
    private String message;
    private String serie;
    private String numero;
    private boolean retriedWithFreshToken;
    private GuideRemissionSubmissionResponse submission;
    private GuideRemissionTicketStatusResponse ticketStatus;
}
