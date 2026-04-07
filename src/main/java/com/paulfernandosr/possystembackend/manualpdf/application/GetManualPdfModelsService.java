package com.paulfernandosr.possystembackend.manualpdf.application;

import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfModel;
import com.paulfernandosr.possystembackend.manualpdf.domain.port.input.GetManualPdfModelsUseCase;
import com.paulfernandosr.possystembackend.manualpdf.domain.port.output.ManualPdfRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetManualPdfModelsService implements GetManualPdfModelsUseCase {

    private final ManualPdfRepository repository;

    @Override
    public List<ManualPdfModel> getModels(int year, Long familyId) {
        return repository.findModelsByYearAndFamily(year, familyId);
    }
}
