package com.paulfernandosr.possystembackend.manualpdf.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.files")
public class ManualPdfProperties {

    private String manualPdfsDir;
}
