package com.paulfernandosr.possystembackend.product.application;

import com.paulfernandosr.possystembackend.product.domain.Product;
import com.paulfernandosr.possystembackend.product.domain.ProductSerialUnit;
import com.paulfernandosr.possystembackend.product.domain.ProductStock;
import com.paulfernandosr.possystembackend.product.domain.ProductStockAdjustment;
import com.paulfernandosr.possystembackend.product.domain.ProductStockAdjustmentCommand;
import com.paulfernandosr.possystembackend.product.domain.ProductStockAdjustmentResult;
import com.paulfernandosr.possystembackend.product.domain.ProductStockMovement;
import com.paulfernandosr.possystembackend.product.domain.exception.InvalidProductException;
import com.paulfernandosr.possystembackend.product.domain.port.input.AdjustProductStockUseCase;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductRepository;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductSerialUnitRepository;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductStockAdjustmentRepository;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductStockMovementRepository;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

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
        String category = product.getCategory() == null ? null : product.getCategory().trim().toUpperCase(Locale.ROOT);

        if (isSerial) {
            validateSerialQuantity(qty);
            validateSerialUnitsRequired(type, category, command.getSerialUnits(), qty);
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
                bajaSerialUnitsFromAdjustment(productId, adjustmentId, command, category);
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
                .lastMovementAt(LocalDateTime.now())
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

    private void validateSerialUnitsRequired(String type, String category, java.util.List<ProductSerialUnit> units, BigDecimal qty) {
        if (units == null || units.isEmpty()) {
            throw new InvalidProductException("serialUnits es obligatorio para productos serializados.");
        }

        if (category == null || (!"MOTOR".equals(category) && !"MOTOCICLETAS".equals(category))) {
            throw new InvalidProductException("Producto serializado debe pertenecer a categoría MOTOR o MOTOCICLETAS.");
        }

        int expected = qty.intValueExact();
        if (units.size() != expected) {
            throw new InvalidProductException("serialUnits debe tener exactamente " + expected + " elemento(s) para quantity=" + qty + ".");
        }

        // Unicidad interna en el request
        Set<String> vins = new HashSet<>();
        Set<String> engines = new HashSet<>();
        Set<String> chassis = new HashSet<>();

        for (ProductSerialUnit u : units) {
            String vin = normalize(u.getVin());
            String eng = normalize(u.getEngineNumber());
            String chs = normalize(u.getChassisNumber());
            String color = normalize(u.getColor());
            Integer year = u.getYearMake();

            if ("IN_ADJUST".equals(type)) {
                // comunes
                if (color == null) {
                    throw new InvalidProductException("color es obligatorio en serialUnits.");
                }
                if (eng == null) {
                    throw new InvalidProductException("engineNumber es obligatorio en serialUnits.");
                }
                if (year == null || year <= 0) {
                    throw new InvalidProductException("yearMake es obligatorio y debe ser > 0 en serialUnits.");
                }

                // DUA opcional (pero consistente)
                String dua = normalize(u.getDuaNumber());
                Integer duaItem = u.getDuaItem();
                if (dua != null || duaItem != null) {
                    if (dua == null) {
                        throw new InvalidProductException("duaNumber no puede ser vacío si se envía duaItem.");
                    }
                    if (duaItem == null || duaItem <= 0) {
                        throw new InvalidProductException("duaItem debe ser > 0 si se envía duaNumber.");
                    }
                }

                if ("MOTOR".equals(category)) {
                    if (vin != null) {
                        throw new InvalidProductException("Para MOTOR no se debe enviar vin.");
                    }
                    if (chs != null) {
                        throw new InvalidProductException("Para MOTOR no se debe enviar chassisNumber.");
                    }
                } else {
                    // MOTOCICLETAS
                    if (vin == null) {
                        throw new InvalidProductException("Para MOTOCICLETAS vin es obligatorio.");
                    }
                    if (chs == null) {
                        throw new InvalidProductException("Para MOTOCICLETAS chassisNumber es obligatorio.");
                    }
                }

            } else {
                // OUT_ADJUST
                if (u.getId() == null && vin == null && eng == null && chs == null) {
                    throw new InvalidProductException("En OUT_ADJUST cada serialUnit debe tener id o vin o engineNumber o chassisNumber.");
                }
                if ("MOTOR".equals(category) && u.getId() == null && eng == null) {
                    throw new InvalidProductException("En OUT_ADJUST para MOTOR debes indicar id o engineNumber.");
                }
            }

            if (vin != null && !vins.add(vin)) {
                throw new InvalidProductException("VIN duplicado en el request: " + vin);
            }
            if (eng != null && !engines.add(eng)) {
                throw new InvalidProductException("engineNumber duplicado en el request: " + eng);
            }
            if (chs != null && !chassis.add(chs)) {
                throw new InvalidProductException("chassisNumber duplicado en el request: " + chs);
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
                    .chassisNumber(u.getChassisNumber())
                    .engineNumber(u.getEngineNumber())
                    .color(u.getColor())
                    .yearMake(u.getYearMake())
                    .duaNumber(u.getDuaNumber())
                    .duaItem(u.getDuaItem())
                    .status("EN_ALMACEN")
                    .build();
            serialUnitRepository.create(toCreate);
        }
    }

    private void bajaSerialUnitsFromAdjustment(Long productId, Long adjustmentId, ProductStockAdjustmentCommand command, String category) {
        Set<Long> touched = new HashSet<>();

        for (ProductSerialUnit u : command.getSerialUnits()) {
            ProductSerialUnit found;

            if (u.getId() != null) {
                found = serialUnitRepository.findAvailableById(productId, u.getId())
                        .orElseThrow(() -> new InvalidProductException("No existe unidad en EN_ALMACEN con id=" + u.getId() + "."));

            } else if (normalize(u.getEngineNumber()) != null) {
                found = serialUnitRepository.findAvailableByEngineNumber(productId, u.getEngineNumber())
                        .orElseThrow(() -> new InvalidProductException("No existe unidad en EN_ALMACEN con engineNumber=" + u.getEngineNumber() + "."));

            } else if (normalize(u.getVin()) != null) {
                found = serialUnitRepository.findAvailableByVin(productId, u.getVin())
                        .orElseThrow(() -> new InvalidProductException("No existe unidad en EN_ALMACEN con vin=" + u.getVin() + "."));

            } else {
                // chassis (solo MOTOCICLETAS en la BD)
                found = serialUnitRepository.findAvailableBySerialNumber(productId, u.getChassisNumber())
                        .orElseThrow(() -> new InvalidProductException("No existe unidad en EN_ALMACEN con chassisNumber=" + u.getChassisNumber() + "."));
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
