package com.paulfernandosr.possystembackend.product.domain.port.output;

import java.util.Set;

public interface ModelWriteRepository {
    void insertMissing(Set<String> models);
}
