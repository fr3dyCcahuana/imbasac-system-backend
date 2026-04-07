package com.paulfernandosr.possystembackend.manualpdf.infrastructure.adapter.input.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
public class ManualPdfCreateRequest {

    @NotNull(message = "El archivo PDF es obligatorio.")
    private MultipartFile file;

    private String familyCode;

    @NotBlank(message = "familyName es obligatorio.")
    private String familyName;

    private Integer familySortOrder = 0;

    private String modelCode;

    @NotBlank(message = "modelName es obligatorio.")
    private String modelName;

    private Integer modelSortOrder = 0;

    private String title;

    @NotNull(message = "yearFrom es obligatorio.")
    private Integer yearFrom;

    @NotNull(message = "yearTo es obligatorio.")
    private Integer yearTo;

    private Boolean enabled = true;
}
