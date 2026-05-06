package com.paulfernandosr.possystembackend.customer.infrastructure.adapter.output.apisnet;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

@Configuration
public class ApisNetConfig {
    @Bean(name = "apisNetRestClient")
    public RestClient apisNetRestClient() {
        return RestClient.builder()
                .baseUrl("https://api.apis.net.pe/v2")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + "apis-token-17450.jJCSnIQl8RhH9jO7LQZ6gFjoYqzSHmB2")
                .build();
    }

    @Bean(name = "eldniRestClient")
    public RestClient eldniRestClient() {
        return RestClient.builder()
                .baseUrl("https://eldni.com")
                .defaultHeader(HttpHeaders.USER_AGENT,
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                                + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
                .build();
    }

    @Bean(name = "sunatRucRestClient")
    public RestClient sunatRucRestClient() {
        return RestClient.builder()
                .baseUrl("https://e-consultaruc.sunat.gob.pe")
                .defaultHeader(HttpHeaders.USER_AGENT,
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                                + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
                .build();
    }
}
