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

    /** True si es dirección fiscal (domicilio fiscal). */
    private boolean fiscal;

    /** Para permitir desactivar direcciones sin borrarlas. */
    private boolean enabled;

    /** Orden/posición opcional (0=fiscal por defecto). */
    private int position;
}
