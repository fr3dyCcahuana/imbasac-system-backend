package com.paulfernandosr.possystembackend.productoffer.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.productoffer.application.ProductBasicOfferService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductBasicOfferExpiryJob {

    private final ProductBasicOfferService offerService;

    @Scheduled(cron = "0 10 0 * * *", zone = "America/Lima")
    public void expireOverdueActiveOffers() {
        int expired = offerService.expireOverdueActiveOffers();
        if (expired > 0) {
            log.info("Ofertas basicas expiradas automaticamente: {}", expired);
        }
    }
}
