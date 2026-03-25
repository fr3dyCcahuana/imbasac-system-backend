package com.paulfernandosr.possystembackend.guideremission.application;

import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionPageCriteria;
import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionPageMetadata;
import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionPageResponse;
import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionPageResult;
import com.paulfernandosr.possystembackend.guideremission.domain.port.input.SearchGuideRemissionsUseCase;
import com.paulfernandosr.possystembackend.guideremission.domain.port.output.GuideRemissionRepository;
import com.paulfernandosr.possystembackend.guideremission.infrastructure.config.GuideRemissionProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SearchGuideRemissionsService implements SearchGuideRemissionsUseCase {
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;

    private final GuideRemissionRepository guideRemissionRepository;
    private final GuideRemissionProperties properties;

    @Override
    @Transactional(readOnly = true)
    public GuideRemissionPageResponse search(GuideRemissionPageCriteria criteria) {
        GuideRemissionPageCriteria normalized = normalize(criteria);
        GuideRemissionPageResult result = guideRemissionRepository.searchPage(properties.getCompany().getRuc(), normalized);

        int totalPages = normalized.getSize() == 0
                ? 0
                : (int) Math.ceil((double) result.getTotalElements() / (double) normalized.getSize());

        return GuideRemissionPageResponse.builder()
                .payload(result.getItems())
                .metadata(GuideRemissionPageMetadata.builder()
                        .page(normalized.getPage())
                        .size(normalized.getSize())
                        .totalElements(result.getTotalElements())
                        .totalPages(totalPages)
                        .hasNext((long) (normalized.getPage() + 1) * normalized.getSize() < result.getTotalElements())
                        .hasPrevious(normalized.getPage() > 0)
                        .build())
                .build();
    }

    private GuideRemissionPageCriteria normalize(GuideRemissionPageCriteria criteria) {
        GuideRemissionPageCriteria source = criteria != null ? criteria : new GuideRemissionPageCriteria();

        int page = source.getPage() == null || source.getPage() < 0 ? DEFAULT_PAGE : source.getPage();
        int size = source.getSize() == null || source.getSize() <= 0 ? DEFAULT_SIZE : Math.min(source.getSize(), MAX_SIZE);

        source.setPage(page);
        source.setSize(size);
        source.setQuery(trimToNull(source.getQuery()));
        source.setStatus(trimToNull(source.getStatus()));
        source.setSerie(trimToNull(source.getSerie()));
        source.setNumero(trimToNull(source.getNumero()));
        source.setRecipientDocumentNumber(trimToNull(source.getRecipientDocumentNumber()));
        source.setRelatedDocument(trimToNull(source.getRelatedDocument()));
        return source;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
