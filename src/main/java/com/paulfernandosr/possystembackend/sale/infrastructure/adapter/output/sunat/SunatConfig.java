package com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.sunat;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@RequiredArgsConstructor
public class SunatConfig {
    private final SunatProps sunatProps;

    @Bean
    public RestClient sunatRestClient() {
        return RestClient.builder()
                .baseUrl(sunatProps.getUrl())
                .build();
    }
}
