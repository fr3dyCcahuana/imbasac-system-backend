package com.paulfernandosr.possystembackend.customer.infrastructure.adapter.output.apisnet;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

@Configuration
public class ApisNetConfig {
    @Bean
    public RestClient apisNetRestClient() {
        return RestClient.builder()
                .baseUrl("https://api.apis.net.pe/v2")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + "apis-token-17450.jJCSnIQl8RhH9jO7LQZ6gFjoYqzSHmB2")
                .build();
    }
}
