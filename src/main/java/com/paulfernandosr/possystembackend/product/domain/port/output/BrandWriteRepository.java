package com.paulfernandosr.possystembackend.product.domain.port.output;

import java.util.Set;

public interface BrandWriteRepository {
    void insertMissing(Set<String> brands);
}
