package com.paulfernandosr.possystembackend.manualpdf.infrastructure.adapter.input.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
public class ManualPdfCreateRequest {

    @NotNull(message = "El archivo PDF es obligatorio.")
    private MultipartFile file;

    @NotNull(message = "modelId es obligatorio.")
    private Long modelId;

    private String title;

    @NotNull(message = "yearFrom es obligatorio.")
    private Integer yearFrom;

    @NotNull(message = "yearTo es obligatorio.")
    private Integer yearTo;

    private Boolean enabled = true;
}
