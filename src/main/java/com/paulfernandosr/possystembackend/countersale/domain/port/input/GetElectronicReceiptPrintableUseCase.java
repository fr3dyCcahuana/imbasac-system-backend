package com.paulfernandosr.possystembackend.countersale.domain.port.input;

import com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.input.dto.ElectronicReceiptPrintableResponse;

public interface GetElectronicReceiptPrintableUseCase {
    ElectronicReceiptPrintableResponse getByComboId(Long comboId);
}
