package com.paulfernandosr.possystembackend.sale.application;

import com.paulfernandosr.possystembackend.common.domain.exception.InvalidPasswordException;
import com.paulfernandosr.possystembackend.common.domain.port.output.PasswordValidationPort;
import com.paulfernandosr.possystembackend.sale.application.query.GetFullSaleSessionInfoQuery;
import com.paulfernandosr.possystembackend.sale.domain.SaleSession;
import com.paulfernandosr.possystembackend.sale.domain.exception.InvalidSaleSessionException;
import com.paulfernandosr.possystembackend.sale.domain.port.input.GetFullSaleSessionInfoUseCase;
import com.paulfernandosr.possystembackend.sale.domain.port.output.SaleSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetFullSaleSessionInfoService implements GetFullSaleSessionInfoUseCase {
    private final SaleSessionRepository saleSessionRepository;
    private final PasswordValidationPort passwordValidationPort;

    @Override
    public SaleSession getFullSaleSessionInfoById(GetFullSaleSessionInfoQuery query) {
        boolean isPasswordValid = passwordValidationPort.isPasswordValid(query.getPassword());

        if (!isPasswordValid) {
            throw new InvalidPasswordException("Invalid password with value: " + query.getPassword());
        }

        return saleSessionRepository.findById(query.getSaleSessionId())
                .orElseThrow(() -> new InvalidSaleSessionException("Invalid sale session with identification: " + query.getSaleSessionId()));
    }
}
