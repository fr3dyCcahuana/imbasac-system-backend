package com.paulfernandosr.possystembackend.whatsapp.domain.port.output;

import com.paulfernandosr.possystembackend.whatsapp.domain.WhatsAppContact;

import java.util.Optional;

public interface WhatsAppContactRepository {
    WhatsAppContact upsertByWaId(String waId, String phoneNumber, String profileName);
    Optional<WhatsAppContact> findByWaId(String waId);
}
