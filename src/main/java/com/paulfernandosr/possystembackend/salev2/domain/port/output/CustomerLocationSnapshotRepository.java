package com.paulfernandosr.possystembackend.salev2.domain.port.output;

import com.paulfernandosr.possystembackend.salev2.domain.model.CustomerLocationSnapshot;

import java.util.Optional;

public interface CustomerLocationSnapshotRepository {

    Optional<CustomerLocationSnapshot> resolveCustomerLocation(Long customerId,
                                                               String customerDocType,
                                                               String customerDocNumber,
                                                               String customerAddress);
}
