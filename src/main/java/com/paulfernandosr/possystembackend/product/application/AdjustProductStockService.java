package com.paulfernandosr.possystembackend.product.application;

import com.paulfernandosr.possystembackend.product.domain.*;
import com.paulfernandosr.possystembackend.product.domain.exception.InvalidProductException;
import com.paulfernandosr.possystembackend.product.domain.port.input.AdjustProductStockUseCase;
import com.paulfernandosr.possystembackend.product.domain.port.output.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AdjustProductStockService implements AdjustProductStockUseCase {

    private static final Set<String> ALLOWED_TYPES = Set.of("IN_ADJUST", "OUT_ADJUST");

    private final ProductRepository productRepository;
    private final ProductStockRepository stockRepository;
    private final ProductStockMovementRepository movementRepository;
    private final ProductStockAdjustmentRepository adjustmentRepository;
    private final ProductSerialUnitRepository serialUnitRepository;

    @Override
    @Transactional
    public ProductStockAdjustmentResult adjust(Long productId, ProductStockAdjustmentCommand command) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new InvalidProductException("Producto no existe."));

        // ✅ Solo se permite ajuste manual cuando facturableSunat = false
        if (Boolean.TRUE.equals(product.getFacturableSunat())) {
            throw new InvalidProductException("No se permite ajustar stock manualmente cuando facturableSunat=true. Cambie facturableSunat a false si el producto es interno/no SUNAT.");
        }

        // ✅ Debe afectar stock
        if (Boolean.FALSE.equals(product.getAffectsStock())) {
            throw new InvalidProductException("No se permite ajustar stock en un producto con affectsStock=false.");
        }

        String type = command.getMovementType() == null ? null : command.getMovementType().trim().toUpperCase(Locale.ROOT);
        if (type == null || !ALLOWED_TYPES.contains(type)) {
            throw new InvalidProductException("movementType inválido. Valores permitidos: IN_ADJUST, OUT_ADJUST.");
        }

        BigDecimal qty = requirePositive(command.getQuantity(), "quantity");

        boolean isSerial = Boolean.TRUE.equals(product.getManageBySerial());
        if (isSerial) {
            validateSerialQuantity(qty);
            validateSerialUnitsRequired(type, command.getSerialUnits(), qty, command.getLocationCode());
        }

        // Lock de stock (si existe)
        ProductStock current = stockRepository.findByProductIdForUpdate(productId)
                .orElse(ProductStock.builder()
                        .productId(productId)
                        .quantityOnHand(BigDecimal.ZERO)
                        .averageCost(null)
                        .lastUnitCost(null)
                        .lastMovementAt(null)
                        .build());

        BigDecimal currentQty = nvl(current.getQuantityOnHand());

        BigDecimal unitCost = command.getUnitCost();
        if ("IN_ADJUST".equals(type)) {
            if (unitCost == null) {
                throw new InvalidProductException("unitCost es obligatorio en IN_ADJUST.");
            }
            if (unitCost.compareTo(BigDecimal.ZERO) < 0) {
                throw new InvalidProductException("unitCost no puede ser negativo.");
            }
        }

        if ("OUT_ADJUST".equals(type) && currentQty.compareTo(qty) < 0) {
            throw new InvalidProductException("Stock insuficiente. Stock actual=" + currentQty + ", solicitado=" + qty + ".");
        }

        // 1) Registrar ajuste
        BigDecimal totalCost = null;
        if ("IN_ADJUST".equals(type)) {
            totalCost = unitCost.multiply(qty).setScale(6, RoundingMode.HALF_UP);
        } else {
            // En salida, usamos costo promedio si existe como referencia contable.
            if (current.getAverageCost() != null) {
                totalCost = current.getAverageCost().multiply(qty).setScale(6, RoundingMode.HALF_UP);
            }
        }

        ProductStockAdjustment createdAdj = adjustmentRepository.create(ProductStockAdjustment.builder()
                .productId(productId)
                .movementType(type)
                .quantity(qty)
                .unitCost("IN_ADJUST".equals(type) ? unitCost : null)
                .totalCost(totalCost)
                .reason(command.getReason())
                .note(command.getNote())
                .createdBy(null)
                .build());

        Long adjustmentId = createdAdj.getId();

        // 2) Seriales: crear unidades / dar de baja
        if (isSerial) {
            if ("IN_ADJUST".equals(type)) {
                createSerialUnitsFromAdjustment(productId, adjustmentId, command);
            } else {
                bajaSerialUnitsFromAdjustment(productId, adjustmentId, command);
            }
        }

        // 3) Actualizar stock
        BigDecimal newQty = "IN_ADJUST".equals(type) ? currentQty.add(qty) : currentQty.subtract(qty);

        BigDecimal newAvg = current.getAverageCost();
        BigDecimal newLast = current.getLastUnitCost();

        if ("IN_ADJUST".equals(type)) {
            BigDecimal oldAvg = current.getAverageCost();
            BigDecimal oldQty = currentQty;
            newAvg = computeWeightedAverage(oldQty, oldAvg, qty, unitCost);
            newLast = unitCost;
        }

        ProductStock newStock = ProductStock.builder()
                .productId(productId)
                .quantityOnHand(newQty)
                .averageCost(newAvg)
                .lastUnitCost(newLast)
                .lastMovementAt(java.time.LocalDateTime.now())
                .build();
        stockRepository.upsert(newStock);

        // 4) Kardex
        ProductStockMovement movement = ProductStockMovement.builder()
                .productId(productId)
                .movementType(type)
                .sourceTable("product_stock_adjustment")
                .sourceId(adjustmentId)
                .quantityIn("IN_ADJUST".equals(type) ? qty : BigDecimal.ZERO)
                .quantityOut("OUT_ADJUST".equals(type) ? qty : BigDecimal.ZERO)
                .unitCost("IN_ADJUST".equals(type) ? unitCost : current.getAverageCost())
                .totalCost(totalCost)
                .balanceQty(newQty)
                .balanceCost(newAvg)
                .build();
        movementRepository.create(movement);

        return ProductStockAdjustmentResult.builder()
                .adjustmentId(adjustmentId)
                .productId(productId)
                .movementType(type)
                .quantity(qty)
                .unitCost("IN_ADJUST".equals(type) ? unitCost : current.getAverageCost())
                .totalCost(totalCost)
                .balanceQty(newQty)
                .balanceCost(newAvg)
                .createdAt(createdAdj.getCreatedAt())
                .build();
    }

    private void validateSerialUnitsRequired(String type, List<ProductSerialUnit> units, BigDecimal qty, String locationCode) {
        if (units == null || units.isEmpty()) {
            throw new InvalidProductException("serialUnits es obligatorio para productos serializados.");
        }
        int expected = qty.intValueExact();
        if (units.size() != expected) {
            throw new InvalidProductException("serialUnits debe tener exactamente " + expected + " elemento(s) para quantity=" + qty + ".");
        }
        // Unicidad interna en el request
        Set<String> vins = new HashSet<>();
        Set<String> engines = new HashSet<>();
        Set<String> serials = new HashSet<>();

        for (ProductSerialUnit u : units) {
            String vin = normalize(u.getVin());
            String eng = normalize(u.getEngineNumber());
            String ser = normalize(u.getSerialNumber());

            if ("IN_ADJUST".equals(type)) {
                // para ingreso, al menos uno de los identificadores debe existir
                if (vin == null && eng == null && ser == null) {
                    throw new InvalidProductException("Cada serialUnit debe tener al menos vin o engineNumber o serialNumber.");
                }
                // set location default si no viene
                if ((u.getLocationCode() == null || u.getLocationCode().isBlank()) && locationCode != null && !locationCode.isBlank()) {
                    u.setLocationCode(locationCode);
                }
            } else {
                // salida: identificador obligatorio
                if (u.getId() == null && vin == null && eng == null && ser == null) {
                    throw new InvalidProductException("En OUT_ADJUST cada serialUnit debe tener id o vin o engineNumber o serialNumber.");
                }
            }

            if (vin != null && !vins.add(vin)) {
                throw new InvalidProductException("VIN duplicado en el request: " + vin);
            }
            if (eng != null && !engines.add(eng)) {
                throw new InvalidProductException("engineNumber duplicado en el request: " + eng);
            }
            if (ser != null && !serials.add(ser)) {
                throw new InvalidProductException("serialNumber duplicado en el request: " + ser);
            }
        }
    }

    private void createSerialUnitsFromAdjustment(Long productId, Long adjustmentId, ProductStockAdjustmentCommand command) {
        for (ProductSerialUnit u : command.getSerialUnits()) {
            ProductSerialUnit toCreate = ProductSerialUnit.builder()
                    .productId(productId)
                    .purchaseItemId(null)
                    .saleItemId(null)
                    .stockAdjustmentId(adjustmentId)
                    .vin(u.getVin())
                    .serialNumber(u.getSerialNumber())
                    .engineNumber(u.getEngineNumber())
                    .color(u.getColor())
                    .yearMake(u.getYearMake())
                    .yearModel(u.getYearModel())
                    .vehicleClass(u.getVehicleClass())
                    .status("EN_ALMACEN")
                    .locationCode(u.getLocationCode() != null ? u.getLocationCode() : command.getLocationCode())
                    .build();
            serialUnitRepository.create(toCreate);
        }
    }

    private void bajaSerialUnitsFromAdjustment(Long productId, Long adjustmentId, ProductStockAdjustmentCommand command) {
        Set<Long> touched = new HashSet<>();

        for (ProductSerialUnit u : command.getSerialUnits()) {
            ProductSerialUnit found = null;

            if (u.getId() != null) {
                found = serialUnitRepository.findAvailableById(productId, u.getId())
                        .orElseThrow(() -> new InvalidProductException("No existe unidad en EN_ALMACEN con id=" + u.getId() + "."));
            } else if (normalize(u.getVin()) != null) {
                found = serialUnitRepository.findAvailableByVin(productId, u.getVin())
                        .orElseThrow(() -> new InvalidProductException("No existe unidad en EN_ALMACEN con vin=" + u.getVin() + "."));
            } else if (normalize(u.getEngineNumber()) != null) {
                found = serialUnitRepository.findAvailableByEngineNumber(productId, u.getEngineNumber())
                        .orElseThrow(() -> new InvalidProductException("No existe unidad en EN_ALMACEN con engineNumber=" + u.getEngineNumber() + "."));
            } else {
                found = serialUnitRepository.findAvailableBySerialNumber(productId, u.getSerialNumber())
                        .orElseThrow(() -> new InvalidProductException("No existe unidad en EN_ALMACEN con serialNumber=" + u.getSerialNumber() + "."));
            }

            if (!touched.add(found.getId())) {
                throw new InvalidProductException("La unidad serial id=" + found.getId() + " está duplicada en el request.");
            }

            serialUnitRepository.markAsBaja(found.getId(), adjustmentId);
        }
    }

    private BigDecimal computeWeightedAverage(BigDecimal oldQty, BigDecimal oldAvg, BigDecimal inQty, BigDecimal inUnitCost) {
        BigDecimal oq = nvl(oldQty);
        BigDecimal nq = oq.add(inQty);
        if (nq.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        BigDecimal baseAvg = oldAvg != null ? oldAvg : inUnitCost;
        BigDecimal totalOld = oq.multiply(baseAvg);
        BigDecimal totalIn = inQty.multiply(inUnitCost);
        return totalOld.add(totalIn)
                .divide(nq, 6, RoundingMode.HALF_UP);
    }

    private BigDecimal requirePositive(BigDecimal value, String field) {
        if (value == null) {
            throw new InvalidProductException(field + " es obligatorio.");
        }
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidProductException(field + " debe ser mayor a 0.");
        }
        return value;
    }

    private void validateSerialQuantity(BigDecimal qty) {
        try {
            qty.intValueExact();
        } catch (ArithmeticException ex) {
            throw new InvalidProductException("Para productos serializados, quantity debe ser entero.");
        }
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private String normalize(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
