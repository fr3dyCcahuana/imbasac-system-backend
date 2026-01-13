package com.paulfernandosr.possystembackend.sale.application;

import com.paulfernandosr.possystembackend.sale.domain.Sale;
import com.paulfernandosr.possystembackend.sale.domain.SaleStatus;
import com.paulfernandosr.possystembackend.sale.domain.exception.InvalidSaleException;
import com.paulfernandosr.possystembackend.sale.domain.exception.SaleNotFoundException;
import com.paulfernandosr.possystembackend.sale.domain.port.input.CancelSaleUseCase;
import com.paulfernandosr.possystembackend.sale.domain.port.output.SaleRepository;
import com.paulfernandosr.possystembackend.user.domain.User;
import com.paulfernandosr.possystembackend.user.domain.port.output.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CancelSaleService implements CancelSaleUseCase {
    private final SaleRepository saleRepository;
    private final UserRepository userRepository;

    @Override
    public void cancelSaleById(Long saleId, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new InvalidSaleException("User not found with identification: " + username));

        if (user.isNotOnRegister()) {
            throw new InvalidSaleException("User isn't on register with identification: " + username);
        }

        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new SaleNotFoundException("Sale not found with identification: " + saleId));

        if (sale.isElectronic()) {
            throw new InvalidSaleException("Sale is electronic with identification: " + saleId);
        }

        if (sale.isCanceled()) {
            throw new InvalidSaleException("Sale is already canceled with identification: " + saleId);
        }

        //sale.setItems(saleRepository.findFullSaleItemsBySaleId(saleId));
        sale.setStatus(SaleStatus.CANCELLED);
        sale.setIssuedAt(LocalDateTime.now());
        sale.setIssuedBy(user);

        saleRepository.cancel(sale);
    }
}
