package com.paulfernandosr.possystembackend.customer.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.customer.domain.Customer;
import com.paulfernandosr.possystembackend.customer.domain.port.output.CustomerProvider;
import com.paulfernandosr.possystembackend.customer.infrastructure.adapter.output.apisnet.JuridicalPerson;
import com.paulfernandosr.possystembackend.customer.infrastructure.adapter.output.apisnet.NaturalPerson;
import com.paulfernandosr.possystembackend.customer.infrastructure.adapter.output.apisnet.PersonMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ApisNetCustomerProvider implements CustomerProvider {
    private final RestClient apisNetRestClient;

    @Override
    public Optional<Customer> findByDni(String dni) {
        NaturalPerson personInfo = apisNetRestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/reniec/dni")
                        .queryParam("numero", dni)
                        .build())
                .retrieve()
                .body(NaturalPerson.class);

        return Optional.ofNullable(personInfo)
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
