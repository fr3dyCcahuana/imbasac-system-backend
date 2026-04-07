package com.paulfernandosr.possystembackend.manualpdf.application;

import com.paulfernandosr.possystembackend.manualpdf.domain.port.input.GetManualPdfYearsUseCase;
import com.paulfernandosr.possystembackend.manualpdf.domain.port.output.ManualPdfRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetManualPdfYearsService implements GetManualPdfYearsUseCase {

    private final ManualPdfRepository repository;

    @Override
    public List<Integer> getYears() {
        return repository.findAvailableYears();
    }
}
