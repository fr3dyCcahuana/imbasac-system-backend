package com.paulfernandosr.possystembackend.manualpdf.application;

import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfFamily;
import com.paulfernandosr.possystembackend.manualpdf.domain.port.input.GetManualPdfFamiliesUseCase;
import com.paulfernandosr.possystembackend.manualpdf.domain.port.output.ManualPdfRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetManualPdfFamiliesService implements GetManualPdfFamiliesUseCase {

    private final ManualPdfRepository repository;

    @Override
    public List<ManualPdfFamily> getFamilies(int year) {
        return repository.findFamiliesByYear(year);
    }
}
