package com.paulfernandosr.possystembackend.customer.domain;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerAddress {
    private Long id;
    private Long customerId;

    /** Dirección completa (texto). */
    private String address;

    /** Ubigeo (6 dígitos, según SUNAT/INEI). */
    private String ubigeo;

    private String department;
    private String province;
    private String district;
    private String phone;
    private String email;

    /** True si es dirección fiscal (domicilio fiscal). */
    private boolean fiscal;

    /** Para permitir desactivar direcciones sin borrarlas. */
    private boolean enabled;

    /** Orden/posición opcional (0=fiscal por defecto). */
    private int position;

    private Double latitude;
    private Double longitude;
    private GeolocationStatus geolocationStatus;
    private GeolocationSource geolocationSource;
    private Double geolocationAccuracyMeters;
    private java.time.LocalDateTime geolocatedAt;
    private Long geolocatedBy;
    private java.time.LocalDateTime geolocationVerifiedAt;
    private Long geolocationVerifiedBy;
    private String geolocationAddressHash;
}
