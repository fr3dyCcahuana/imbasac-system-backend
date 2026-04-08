package com.paulfernandosr.possystembackend.manualpdf.infrastructure.adapter.input.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ManualPdfFamilyCreateRequest {

    private String code;

    @NotBlank(message = "name es obligatorio.")
    private String name;

    private Integer sortOrder = 0;

    private Boolean enabled = true;
}
