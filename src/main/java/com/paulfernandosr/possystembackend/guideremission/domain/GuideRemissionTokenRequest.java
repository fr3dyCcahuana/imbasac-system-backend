package com.paulfernandosr.possystembackend.guideremission.domain;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GuideRemissionTokenRequest {
    @NotBlank
    private String guiasClientId;

    @NotBlank
    private String guiasClientSecret;

    @NotBlank
    private String ruc;

    @NotBlank
    private String usuSecundarioProduccionUser;

    @NotBlank
    private String usuSecundarioProduccionPassword;
}
