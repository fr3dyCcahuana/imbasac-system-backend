package com.paulfernandosr.possystembackend.customer.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.customer.domain.Customer;
import com.paulfernandosr.possystembackend.customer.domain.port.output.CustomerProvider;
import com.paulfernandosr.possystembackend.customer.infrastructure.adapter.output.apisnet.JuridicalPerson;
import com.paulfernandosr.possystembackend.customer.infrastructure.adapter.output.apisnet.PersonMapper;
import com.paulfernandosr.possystembackend.customer.infrastructure.adapter.output.eldni.EldniDniScraper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Component
public class ApisNetCustomerProvider implements CustomerProvider {
    private final RestClient apisNetRestClient;
    private final EldniDniScraper eldniDniScraper;

    public ApisNetCustomerProvider(@Qualifier("apisNetRestClient") RestClient apisNetRestClient,
                                   EldniDniScraper eldniDniScraper) {
        this.apisNetRestClient = apisNetRestClient;
        this.eldniDniScraper = eldniDniScraper;
    }

    @Override
    public Optional<Customer> findByDni(String dni) {
        return eldniDniScraper.findByDni(dni)
                .map(PersonMapper::toCustomer);
    }

    @Override
    public Optional<Customer> findByRuc(String ruc) {
        JuridicalPerson personInfo = apisNetRestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/sunat/ruc/full")
                        .queryParam("numero", ruc)
                        .build())
                .retrieve()
                .body(JuridicalPerson.class);

        return Optional.ofNullable(personInfo)
                .map(PersonMapper::toCustomer);
    }
}
