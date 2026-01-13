package com.paulfernandosr.possystembackend.sale.application;

import com.paulfernandosr.possystembackend.common.domain.Page;
import com.paulfernandosr.possystembackend.common.domain.Pageable;
import com.paulfernandosr.possystembackend.sale.domain.SaleSession;
import com.paulfernandosr.possystembackend.sale.domain.port.input.GetPageOfSaleSessionsUseCase;
import com.paulfernandosr.possystembackend.sale.domain.port.output.SaleSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetPageOfSaleSessionsService implements GetPageOfSaleSessionsUseCase {
    private final SaleSessionRepository saleSessionRepository;

    @Override
    public Page<SaleSession> getPageOfSaleSessions(String query, Pageable pageable) {
        return saleSessionRepository.findPage(query, pageable);
    }
}
