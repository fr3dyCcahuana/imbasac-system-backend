package com.paulfernandosr.possystembackend.supplier.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.customer.infrastructure.adapter.output.apisnet.JuridicalPerson;
import com.paulfernandosr.possystembackend.supplier.domain.Supplier;
import com.paulfernandosr.possystembackend.supplier.domain.port.output.SupplierProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ApisNetSupplierProvider implements SupplierProvider {

    private final RestClient apisNetRestClient;

    @Override
    public Optional<Supplier> findByRuc(String ruc) {
        JuridicalPerson personInfo = apisNetRestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/sunat/ruc/full")
                        .queryParam("numero", ruc)
                        .build())
                .retrieve()
                .body(JuridicalPerson.class);

        return Optional.ofNullable(personInfo)
                .map(SupplierPersonMapper::toSupplier);
    }
}
