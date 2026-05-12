package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ContractSunatDraftPreviewResponse extends ContractSunatDraftResponse {
    private List<String> warnings;
}
