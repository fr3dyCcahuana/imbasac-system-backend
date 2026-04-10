package com.paulfernandosr.possystembackend.countersale.application;

import com.paulfernandosr.possystembackend.countersale.domain.exception.InvalidCounterSaleException;
import com.paulfernandosr.possystembackend.countersale.domain.port.input.GetCounterSaleUseCase;
import com.paulfernandosr.possystembackend.countersale.domain.port.output.CounterSaleQueryRepository;
import com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.input.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetCounterSaleService implements GetCounterSaleUseCase {

    private final CounterSaleQueryRepository counterSaleQueryRepository;

    @Override
    public CounterSaleDetailResponse getById(Long counterSaleId) {
        CounterSaleDetailResponse header = counterSaleQueryRepository.findCounterSaleDetail(counterSaleId);
        if (header == null) {
            throw new InvalidCounterSaleException("Operación de ventanilla no encontrada: " + counterSaleId);
        }

        List<CounterSaleItemResponse> items = counterSaleQueryRepository.findCounterSaleItems(counterSaleId);
        Map<Long, List<CounterSaleSerialUnitResponse>> serialsByItemId = counterSaleQueryRepository
                .findCounterSaleItemSerialUnits(counterSaleId)
                .stream()
                .collect(Collectors.groupingBy(CounterSaleSerialUnitResponse::getCounterSaleItemId));
        items.forEach(item -> item.setSerialUnits(serialsByItemId.getOrDefault(item.getCounterSaleItemId(), List.of())));

        header.setItems(items);
        header.setPayment(counterSaleQueryRepository.findCounterSalePayment(counterSaleId));
        return header;
    }
}
