package com.paulfernandosr.possystembackend.proformav2.domain.port.output;

import com.paulfernandosr.possystembackend.proformav2.domain.ProformaItem;

import java.util.List;

public interface ProformaItemRepository {
    void batchCreate(List<ProformaItem> items);
    List<ProformaItem> findByProformaId(Long proformaId);
}
