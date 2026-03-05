package com.paulfernandosr.possystembackend.contracts.domain.port.output;

import com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.output.row.ProductContractRow;

public interface ContractProductRepository {
    ProductContractRow findById(Long productId);
}
