package com.paulfernandosr.possystembackend.purchase.application;/*
package com.paulfernandosr.possystembackend.purchase.application;

import com.paulfernandosr.possystembackend.purchase.domain.Purchase;
import com.paulfernandosr.possystembackend.purchase.domain.port.input.CreatePurchaseUseCase;
import com.paulfernandosr.possystembackend.purchase.domain.port.output.PurchaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreatePurchaseService implements CreatePurchaseUseCase {

    private final PurchaseRepository purchaseRepository;

    @Override
    @Transactional
    public Purchase createPurchase(Purchase purchase) {
        // Si quieres, aquí puedes agregar lógica de recálculo de totales:
        // validateAndRecalculateTotals(purchase);
        return purchaseRepository.create(purchase);
    }

    // private void validateAndRecalculateTotals(Purchase purchase) {
    //   TODO: implementar reglas de IGV, descuentos y prorrateo de flete
    // }
}
*/
