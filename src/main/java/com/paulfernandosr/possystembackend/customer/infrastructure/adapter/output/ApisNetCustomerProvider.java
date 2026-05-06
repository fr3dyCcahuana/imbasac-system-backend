package com.paulfernandosr.possystembackend.customer.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.customer.domain.Customer;
import com.paulfernandosr.possystembackend.customer.domain.port.output.CustomerProvider;
import com.paulfernandosr.possystembackend.customer.infrastructure.adapter.output.apisnet.PersonMapper;
import com.paulfernandosr.possystembackend.customer.infrastructure.adapter.output.eldni.EldniDniScraper;
import com.paulfernandosr.possystembackend.customer.infrastructure.adapter.output.sunat.SunatRucScraper;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ApisNetCustomerProvider implements CustomerProvider {
    private final EldniDniScraper eldniDniScraper;
    private final SunatRucScraper sunatRucScraper;

    public ApisNetCustomerProvider(EldniDniScraper eldniDniScraper,
                                   SunatRucScraper sunatRucScraper) {
        this.eldniDniScraper = eldniDniScraper;
        this.sunatRucScraper = sunatRucScraper;
    }

    @Override
    public Optional<Customer> findByDni(String dni) {
        return eldniDniScraper.findByDni(dni)
                .map(PersonMapper::toCustomer);
    }

    @Override
    public Optional<Customer> findByRuc(String ruc) {
        return sunatRucScraper.findByRuc(ruc)
                .map(PersonMapper::toCustomer);
    }
}
