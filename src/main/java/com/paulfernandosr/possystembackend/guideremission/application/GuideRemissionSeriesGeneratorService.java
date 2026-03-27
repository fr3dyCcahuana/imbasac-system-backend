package com.paulfernandosr.possystembackend.guideremission.application;

import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionData;
import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionGeneratedSeries;
import com.paulfernandosr.possystembackend.guideremission.domain.port.output.GuideRemissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GuideRemissionSeriesGeneratorService {
    private final GuideRemissionRepository guideRemissionRepository;

    public void assignNextSeriesAndNumber(GuideRemissionData guide) {
        GuideRemissionGeneratedSeries generated = guideRemissionRepository.reserveNextGuideRemissionSeries();
        guide.setSerie(generated.getSerie());
        guide.setNumero(generated.getNumero());
    }
}
