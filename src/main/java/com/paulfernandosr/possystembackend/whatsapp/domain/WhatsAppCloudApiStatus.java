package com.paulfernandosr.possystembackend.whatsapp.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhatsAppCloudApiStatus {
    private boolean ok;
    private String apiVersion;
    private String phoneNumberId;
    private String displayPhoneNumber;
    private String verifiedName;
    private String message;
    private String errorCode;
    private String errorSubcode;
    private String fbtraceId;
}
