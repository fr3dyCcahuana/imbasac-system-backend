package com.paulfernandosr.possystembackend.stock.domain.port.output;

import com.paulfernandosr.possystembackend.stock.domain.Stock;
import com.paulfernandosr.possystembackend.stock.domain.StockMovement;

import java.util.Optional;

public interface StockRepository {

    Optional<Stock> findByProductIdForUpdate(Long productId);

    Stock saveOrUpdateStock(Stock stock);

    StockMovement createMovement(StockMovement movement);
}
