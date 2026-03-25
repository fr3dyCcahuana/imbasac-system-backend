package com.paulfernandosr.possystembackend.guideremission.domain.port.input;

public interface GenerateGuideRemissionPdfUseCase {
    byte[] generate(String serie, String numero);
}
