package com.paulfernandosr.possystembackend.salev2.domain.port.output;

import java.math.BigDecimal;

public interface ProductStockRepository {

    /**
     * Descuenta stock de manera segura.
     * Debe fallar si no hay stock suficiente.
     */
    void decreaseOnHand(Long productId, BigDecimal quantity);

    /**
     * Obtiene stock disponible (informativo). No reemplaza la validación transaccional.
     */
    BigDecimal getOnHand(Long productId);
}
