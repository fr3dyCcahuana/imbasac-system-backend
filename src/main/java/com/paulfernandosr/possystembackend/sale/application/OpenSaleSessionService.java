package com.paulfernandosr.possystembackend.sale.application;

import com.paulfernandosr.possystembackend.sale.domain.SaleSession;
import com.paulfernandosr.possystembackend.sale.domain.exception.InvalidSaleSessionException;
import com.paulfernandosr.possystembackend.sale.domain.port.input.OpenSaleSessionUseCase;
import com.paulfernandosr.possystembackend.sale.domain.port.output.SaleSessionRepository;
import com.paulfernandosr.possystembackend.station.domain.Station;
import com.paulfernandosr.possystembackend.station.domain.port.output.StationRepository;
import com.paulfernandosr.possystembackend.user.domain.User;
import com.paulfernandosr.possystembackend.user.domain.port.output.UserRepository;
import com.paulfernandosr.possystembackend.user.domain.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OpenSaleSessionService implements OpenSaleSessionUseCase {
    private final SaleSessionRepository saleSessionRepository;
    private final UserService userService;
    private final UserRepository userRepository;
    private final StationRepository stationRepository;

    @Override
    public void openSaleSession(SaleSession saleSession) {
        Station station = stationRepository.findById(saleSession.getStation().getId())
                .orElseThrow(() -> new InvalidSaleSessionException("Invalid sale session with station identification: " + saleSession.getStation().getId()));

        if (station.isOpen()) {
            throw new InvalidSaleSessionException("Station is open with identification: " + saleSession.getStation().getId());
        }

        User user = userRepository.findById(saleSession.getUser().getId())
                .orElseThrow(() -> new InvalidSaleSessionException("Invalid sale session with user identification: " + saleSession.getUser().getId()));

        if (user.isOnRegister()) {
            throw new InvalidSaleSessionException("User is on register with identification: " + saleSession.getUser().getId());
        }

        saleSession.setSalesIncome(BigDecimal.ZERO);
        saleSession.setTotalDiscount(BigDecimal.ZERO);
        saleSession.setTotalExpenses(BigDecimal.ZERO);
        saleSession.setOpenedAt(LocalDateTime.now());
        saleSession.setClosedAt(null);

        saleSessionRepository.create(saleSession);
    }
}
