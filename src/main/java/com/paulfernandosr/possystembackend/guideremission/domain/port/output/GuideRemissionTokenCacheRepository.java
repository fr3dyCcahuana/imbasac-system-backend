package com.paulfernandosr.possystembackend.guideremission.domain.port.output;

import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionTokenResponse;

import java.util.Optional;

public interface GuideRemissionTokenCacheRepository {
    Optional<GuideRemissionTokenResponse> findByCompanyRuc(String companyRuc);

    void save(String companyRuc, GuideRemissionTokenResponse tokenResponse);

    void delete(String companyRuc);
}
