package com.paulfernandosr.possystembackend.product.domain.port.output;

import java.util.Set;

public interface CategoryWriteRepository {
    void insertMissing(Set<String> categoryNames);
}
