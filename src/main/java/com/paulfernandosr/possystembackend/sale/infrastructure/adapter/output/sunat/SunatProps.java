package com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.sunat;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "sale.sunat")
public class SunatProps {
    private String mode;
    private String url;
    private String username;
    private String password;
    private Business business;

    @Getter
    @Setter
    public static class Business {
        private String ruc;
        private String businessName;
        private String tradeName;
        private String taxAddress;
        private String ubigeo;
        private String neighborhood;
        private String district;
        private String province;
        private String department;
    }
}
