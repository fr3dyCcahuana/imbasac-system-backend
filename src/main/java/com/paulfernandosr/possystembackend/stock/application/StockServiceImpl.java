package com.paulfernandosr.possystembackend.stock.application;

import com.paulfernandosr.possystembackend.stock.domain.Stock;
import com.paulfernandosr.possystembackend.stock.domain.StockMovement;
import com.paulfernandosr.possystembackend.stock.domain.port.input.StockService;
import com.paulfernandosr.possystembackend.stock.domain.port.output.StockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
public class StockServiceImpl implements StockService {

    private final StockRepository stockRepository;

    public StockServiceImpl(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    @Override
    @Transactional
    public void registerInbound(Long productId,
                                BigDecimal quantity,
                                BigDecimal unitCost,
                                String movementType,
                                String sourceTable,
                                Long sourceId) {

        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return; // nada que hacer
        }

        // 1) Leer stock actual con bloqueo
        Stock stock = stockRepository.findByProductIdForUpdate(productId)
                .orElseGet(() -> {
                    Stock s = new Stock();
                    s.setProductId(productId);
                    s.setQuantityOnHand(BigDecimal.ZERO);
                    s.setAverageCost(BigDecimal.ZERO);
                    s.setLastUnitCost(BigDecimal.ZERO);
                    s.setLastMovementAt(LocalDateTime.now());
                    return s;
                });

        BigDecimal oldQty = stock.getQuantityOnHand();
        BigDecimal oldAvgCost = stock.getAverageCost() != null ? stock.getAverageCost() : BigDecimal.ZERO;

        BigDecimal qtyIn = quantity;
        BigDecimal unitIn = unitCost != null ? unitCost : BigDecimal.ZERO;

        BigDecimal newQty = oldQty.add(qtyIn);

        BigDecimal newAvgCost;
        if (oldQty.compareTo(BigDecimal.ZERO) == 0) {
            newAvgCost = unitIn;
        } else {
            BigDecimal totalOld = oldQty.multiply(oldAvgCost);
            BigDecimal totalNew = qtyIn.multiply(unitIn);
            newAvgCost = totalOld.add(totalNew)
                    .divide(newQty, 6, RoundingMode.HALF_UP);
        }

        // 2) Actualizar stock
        stock.setQuantityOnHand(newQty);
        stock.setAverageCost(newAvgCost);
        stock.setLastUnitCost(unitIn);
        stock.setLastMovementAt(LocalDateTime.now());

        stockRepository.saveOrUpdateStock(stock);

        // 3) Registrar movimiento
        StockMovement movement = new StockMovement();
        movement.setProductId(productId);
        movement.setMovementType(movementType);
        movement.setSourceTable(sourceTable);
        movement.setSourceId(sourceId);
        movement.setQuantityIn(qtyIn);
        movement.setQuantityOut(BigDecimal.ZERO);
        movement.setUnitCost(unitIn);
        movement.setTotalCost(unitIn.multiply(qtyIn));
        movement.setBalanceQty(newQty);
        movement.setBalanceCost(newAvgCost);
        movement.setCreatedAt(LocalDateTime.now());

        stockRepository.createMovement(movement);
    }

    @Override
    @Transactional
    public void registerOutbound(Long productId,
                                 BigDecimal quantity,
                                 BigDecimal unitCost,
                                 String movementType,
                                 String sourceTable,
                                 Long sourceId) {

        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        // 1) Leer stock actual con bloqueo
        Stock stock = stockRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new IllegalStateException("No hay stock registrado para el producto " + productId));

        BigDecimal oldQty = stock.getQuantityOnHand();
        BigDecimal avgCost = stock.getAverageCost() != null ? stock.getAverageCost() : BigDecimal.ZERO;
        BigDecimal qtyOut = quantity;

        // REGLA: no stock negativo
        if (oldQty.compareTo(qtyOut) < 0) {
            throw new IllegalStateException("Stock insuficiente para el producto " + productId);
        }

        BigDecimal newQty = oldQty.subtract(qtyOut);

        // Para promedio ponderado simple, el costo promedio se mantiene
        // a menos que quieras política distinta.
        BigDecimal newAvgCost = avgCost;

        stock.setQuantityOnHand(newQty);
        stock.setAverageCost(newAvgCost);
        stock.setLastMovementAt(LocalDateTime.now());

        stockRepository.saveOrUpdateStock(stock);

        BigDecimal costForOutbound = (unitCost != null && unitCost.compareTo(BigDecimal.ZERO) > 0)
                ? unitCost
                : avgCost;

        // 3) Movimiento de salida
        StockMovement movement = new StockMovement();
        movement.setProductId(productId);
        movement.setMovementType(movementType);
        movement.setSourceTable(sourceTable);
        movement.setSourceId(sourceId);
        movement.setQuantityIn(BigDecimal.ZERO);
        movement.setQuantityOut(qtyOut);
        movement.setUnitCost(costForOutbound);
        movement.setTotalCost(costForOutbound.multiply(qtyOut));
        movement.setBalanceQty(newQty);
        movement.setBalanceCost(newAvgCost);
        movement.setCreatedAt(LocalDateTime.now());

        stockRepository.createMovement(movement);
    }
}
