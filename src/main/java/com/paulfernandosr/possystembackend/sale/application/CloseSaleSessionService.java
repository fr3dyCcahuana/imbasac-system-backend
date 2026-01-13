package com.paulfernandosr.possystembackend.sale.application;

import com.paulfernandosr.possystembackend.sale.domain.SaleSession;
import com.paulfernandosr.possystembackend.sale.domain.exception.InvalidSaleSessionException;
import com.paulfernandosr.possystembackend.sale.domain.port.input.CloseSaleSessionUseCase;
import com.paulfernandosr.possystembackend.sale.domain.port.output.SaleSessionRepository;
import com.paulfernandosr.possystembackend.security.domain.port.output.SessionRepository;
import com.paulfernandosr.possystembackend.user.domain.User;
import com.paulfernandosr.possystembackend.user.domain.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CloseSaleSessionService implements CloseSaleSessionUseCase {
    private final SessionRepository sessionRepository;
    private final SaleSessionRepository saleSessionRepository;
    private final UserService userService;

    @Override
    public void closeSaleSessionById(Long saleSessionId) {
        SaleSession saleSession = saleSessionRepository.findById(saleSessionId)
                .orElseThrow(() -> new InvalidSaleSessionException("Invalid sale session with identification: " + saleSessionId));

        saleSessionRepository.closeById(saleSessionId);

        User user = saleSession.getUser();

        if (user.isCashier()) {
            sessionRepository.deleteByUsername(user.getUsername());
        }
    }
}
