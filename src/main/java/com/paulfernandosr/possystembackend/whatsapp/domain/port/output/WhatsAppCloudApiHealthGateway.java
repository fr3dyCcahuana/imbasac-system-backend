package com.paulfernandosr.possystembackend.whatsapp.domain.port.output;

import com.paulfernandosr.possystembackend.whatsapp.domain.WhatsAppCloudApiStatus;

public interface WhatsAppCloudApiHealthGateway {
    WhatsAppCloudApiStatus checkConnection();
}
