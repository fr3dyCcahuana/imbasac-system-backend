package com.paulfernandosr.possystembackend.purchase.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paulfernandosr.possystembackend.purchase.domain.Purchase;
import com.paulfernandosr.possystembackend.purchase.domain.PurchaseItem;
import com.paulfernandosr.possystembackend.purchase.domain.PurchaseSerialUnit;
import com.paulfernandosr.possystembackend.purchase.domain.exception.DuplicatePurchaseDocumentException;
import com.paulfernandosr.possystembackend.purchase.domain.exception.PurchaseApiException;
import com.paulfernandosr.possystembackend.purchase.domain.exception.PurchaseFieldError;
import com.paulfernandosr.possystembackend.purchase.domain.model.ProductFlags;
import com.paulfernandosr.possystembackend.purchase.domain.model.SerialIdentifierConflict;
import com.paulfernandosr.possystembackend.purchase.domain.port.input.UpdatePurchaseUseCase;
import com.paulfernandosr.possystembackend.purchase.domain.port.output.ProductFlagsRepository;
import com.paulfernandosr.possystembackend.purchase.domain.port.output.ProductSerialUnitRepository;
import com.paulfernandosr.possystembackend.purchase.domain.port.output.PurchaseRepository;
import com.paulfernandosr.possystembackend.stock.domain.port.input.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Year;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UpdatePurchaseService implements UpdatePurchaseUseCase {

    private static final Pattern VEHICLE_IDENTIFIER_PATTERN =
            Pattern.compile("^[A-Z0-9](?:[A-Z0-9 ./_#*+-]*[A-Z0-9])?$");

    private final PurchaseRepository purchaseRepository;
    private final ProductFlagsRepository productFlagsRepository;
    private final ProductSerialUnitRepository productSerialUnitRepository;
    private final StockService stockService;
    private final PurchaseStockEntryService stockEntryService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public Purchase updatePurchase(Long purchaseId, Purchase request, String username) {
        String actor = resolveActor(username);

        if (purchaseId == null) {
            throw new PurchaseApiException(422, "INVALID_PURCHASE_ID", "purchaseId es obligatorio.");
        }
        if (request == null) {
            throw new PurchaseApiException(422, "INVALID_PURCHASE", "La compra es obligatoria.");
        }
        String editReason = normalizeText(request.getEditReason());
        if (editReason == null || editReason.length() < 5) {
            throw new PurchaseApiException(422, "EDIT_REASON_REQUIRED", "Debe indicar un motivo de edición válido.",
                    List.of(PurchaseFieldError.builder()
                            .path("editReason")
                            .message("Motivo de edición obligatorio, mínimo 5 caracteres")
                            .build()));
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new PurchaseApiException(422, "INVALID_PURCHASE_ITEMS", "La compra debe tener al menos un ítem activo.");
        }

        Purchase before = purchaseRepository.findByIdWithItemsForUpdate(purchaseId)
                .orElseThrow(() -> new PurchaseApiException(404, "PURCHASE_NOT_FOUND", "Compra no encontrada."));

        if ("ANULADA".equalsIgnoreCase(before.getStatus())) {
            throw new PurchaseApiException(422, "PURCHASE_ALREADY_CANCELLED", "No se puede editar una compra anulada.");
        }
        boolean stockAlreadyLoaded = !PurchaseStockEntryService.STATUS_PENDING.equalsIgnoreCase(before.getStockEntryStatus());

        request.setId(purchaseId);
        request.setStatus(before.getStatus());

        if (purchaseRepository.existsDocumentForAnotherPurchase(
                purchaseId,
                request.getSupplierRuc(),
                request.getDocumentType(),
                request.getDocumentSeries(),
                request.getDocumentNumber()
        )) {
            throw new DuplicatePurchaseDocumentException(
                    "Ya existe una compra registrada con el mismo proveedor y documento ("
                            + request.getSupplierRuc() + " - "
                            + request.getDocumentType() + " "
                            + request.getDocumentSeries() + "-"
                            + request.getDocumentNumber() + ")."
            );
        }

        Map<Long, PurchaseItem> currentById = safeItems(before).stream()
                .filter(it -> it.getId() != null)
                .collect(Collectors.toMap(PurchaseItem::getId, Function.identity()));

        List<PurchaseItem> incomingItems = request.getItems();
        Map<Long, PurchaseItem> incomingExistingById = incomingItems.stream()
                .filter(it -> it.getId() != null)
                .collect(Collectors.toMap(PurchaseItem::getId, Function.identity(), (a, b) -> {
                    throw new PurchaseApiException(422, "DUPLICATE_PURCHASE_ITEM_ID", "Hay ítems repetidos en el request.");
                }));

        validateIncomingItemsBelongToPurchase(currentById, incomingExistingById);

        int editNumber = purchaseRepository.getNextEditNumber(purchaseId);
        String beforeJson = toJson(before);

        // Cabecera: se actualiza una sola vez. El detalle se corrige por diferencias.
        purchaseRepository.updateHeaderForEdit(request, actor, editReason);

        int nextLine = safeItems(before).stream()
                .map(PurchaseItem::getLineNumber)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0) + 1;

        // 1) Remover ítems que ya no vinieron en el request.
        for (PurchaseItem current : safeItems(before)) {
            if (current.getId() == null) continue;
            if (!incomingExistingById.containsKey(current.getId())) {
                removeExistingItem(before.getId(), current, actor, editReason, stockAlreadyLoaded);
            }
        }

        // 2) Actualizar ítems existentes y crear los nuevos.
        for (int i = 0; i < incomingItems.size(); i++) {
            PurchaseItem incoming = incomingItems.get(i);
            if (incoming.getId() == null) {
                incoming.setLineNumber(nextLine++);
                addNewItem(purchaseId, incoming, i, stockAlreadyLoaded);
            } else {
                PurchaseItem current = currentById.get(incoming.getId());
                updateExistingItem(purchaseId, current, incoming, i, stockAlreadyLoaded);
            }
        }

        Purchase after = purchaseRepository.findByIdWithItems(purchaseId)
                .orElseThrow(() -> new PurchaseApiException(404, "PURCHASE_NOT_FOUND_AFTER_UPDATE", "No se pudo recargar la compra actualizada."));
        String afterJson = toJson(after);

        purchaseRepository.insertEditHistory(purchaseId, editNumber, editReason, actor, beforeJson, afterJson);

        if (!stockAlreadyLoaded && stockEntryService.isDueForStockEntry(after)) {
            return stockEntryService.loadStockIfPending(purchaseId, actor);
        }

        return after;
    }

    private void validateIncomingItemsBelongToPurchase(Map<Long, PurchaseItem> currentById,
                                                       Map<Long, PurchaseItem> incomingExistingById) {
        for (Long itemId : incomingExistingById.keySet()) {
            if (!currentById.containsKey(itemId)) {
                throw new PurchaseApiException(422, "PURCHASE_ITEM_NOT_FOUND",
                        "El ítem " + itemId + " no pertenece a la compra o no está activo.");
            }
        }
    }

    private void addNewItem(Long purchaseId, PurchaseItem item, int itemIndex, boolean stockAlreadyLoaded) {
        ProductFlags flags = getFlagsOrThrow(item.getProductId(), itemIndex);
        boolean affectsStock = Boolean.TRUE.equals(flags.getAffectsStock());
        boolean manageBySerial = Boolean.TRUE.equals(flags.getManageBySerial());
        String category = normalizeCategory(flags.getCategory());

        validateBasicItem(item, itemIndex);
        validateSerialRulesForItem(item, itemIndex, flags, Set.of());

        if (manageBySerial) {
            assertSerialConflicts(item, itemIndex, Set.of());
        }

        Long itemId = purchaseRepository.insertItem(purchaseId, item);

        if (affectsStock && stockAlreadyLoaded) {
            stockService.registerInbound(
                    item.getProductId(),
                    item.getQuantity(),
                    item.getUnitCost(),
                    "IN_PURCHASE_EDIT",
                    "purchase_item",
                    itemId
            );
        }

        if (manageBySerial && stockAlreadyLoaded) {
            // La validación ya normalizó los seriales.
            productSerialUnitRepository.insertInboundSerialUnits(
                    itemId,
                    item.getProductId(),
                    item.getSerialUnits()
            );
        } else if (manageBySerial) {
            productSerialUnitRepository.insertPendingInboundSerialUnits(
                    itemId,
                    item.getProductId(),
                    item.getSerialUnits()
            );
        }
    }

    private void updateExistingItem(Long purchaseId,
                                    PurchaseItem current,
                                    PurchaseItem incoming,
                                    int itemIndex,
                                    boolean stockAlreadyLoaded) {
        ProductFlags flags = getFlagsOrThrow(current.getProductId(), itemIndex);
        boolean affectsStock = Boolean.TRUE.equals(flags.getAffectsStock());
        boolean manageBySerial = Boolean.TRUE.equals(flags.getManageBySerial());

        validateBasicItem(incoming, itemIndex);

        if (!Objects.equals(current.getProductId(), incoming.getProductId())) {
            throw new PurchaseApiException(422, "PURCHASE_ITEM_PRODUCT_CHANGE_NOT_ALLOWED",
                    "No se permite cambiar el producto de una línea existente. Quite la línea y agregue otra, siempre que el stock/serial esté libre.",
                    List.of(PurchaseFieldError.builder()
                            .path(path(itemIndex, "productId"))
                            .message("productId no puede cambiar en una línea existente")
                            .value(incoming.getProductId())
                            .expected(current.getProductId())
                            .build()));
        }

        if (manageBySerial) {
            updateExistingSerialItem(purchaseId, current, incoming, itemIndex, flags);
            return;
        }

        if (hasChangedMoney(current.getUnitCost(), incoming.getUnitCost())) {
            throw new PurchaseApiException(422, "PURCHASE_ITEM_COST_CHANGE_NOT_ALLOWED",
                    "Por seguridad, no se permite cambiar el costo unitario de una línea existente. Use ajuste de costo o registre una línea nueva.",
                    List.of(PurchaseFieldError.builder()
                            .path(path(itemIndex, "unitCost"))
                            .message("unitCost no puede cambiar en una línea existente")
                            .value(incoming.getUnitCost())
                            .expected(current.getUnitCost())
                            .build()));
        }

        BigDecimal diff = nvl(incoming.getQuantity()).subtract(nvl(current.getQuantity()));
        if (affectsStock && stockAlreadyLoaded && diff.compareTo(BigDecimal.ZERO) > 0) {
            stockService.registerInbound(
                    current.getProductId(),
                    diff,
                    current.getUnitCost(),
                    "IN_PURCHASE_EDIT",
                    "purchase_item",
                    current.getId()
            );
        } else if (affectsStock && stockAlreadyLoaded && diff.compareTo(BigDecimal.ZERO) < 0) {
            BigDecimal qtyToReverse = diff.abs();
            assertStockAvailable(current.getProductId(), qtyToReverse,
                    "No se puede reducir la cantidad porque el stock actual no alcanza para revertir la diferencia.");
            stockService.registerOutbound(
                    current.getProductId(),
                    qtyToReverse,
                    null,
                    "OUT_PURCHASE_EDIT",
                    "purchase_item",
                    current.getId()
            );
        }

        incoming.setId(current.getId());
        incoming.setPurchaseId(purchaseId);
        incoming.setProductId(current.getProductId());
        incoming.setLineNumber(current.getLineNumber()); // evita conflictos con uq_purchase_item_line
        purchaseRepository.updateItemForEdit(purchaseId, incoming);
    }

    private void updateExistingSerialItem(Long purchaseId,
                                          PurchaseItem current,
                                          PurchaseItem incoming,
                                          int itemIndex,
                                          ProductFlags flags) {
        if (nvl(current.getQuantity()).compareTo(nvl(incoming.getQuantity())) != 0) {
            throw new PurchaseApiException(422, "SERIAL_ITEM_QUANTITY_CHANGE_NOT_ALLOWED",
                    "En esta primera versión segura no se permite cambiar la cantidad de una línea serializada existente. Agregue otra línea para nuevos seriales o elimine la línea solo si todos sus seriales están libres.");
        }

        if (hasChangedMoney(current.getUnitCost(), incoming.getUnitCost())) {
            throw new PurchaseApiException(422, "SERIAL_ITEM_COST_CHANGE_NOT_ALLOWED",
                    "No se permite cambiar el costo unitario de una línea serializada existente.");
        }

        int blocked = productSerialUnitRepository.countBlockedSerialUnitsByPurchaseItemId(current.getId());
        if (blocked > 0) {
            throw new PurchaseApiException(422, "SERIAL_ITEM_ALREADY_USED",
                    "No se puede editar esta línea porque uno o más seriales ya fueron vendidos, reservados, usados en contrato o ventanilla.");
        }

        List<PurchaseSerialUnit> currentSerials = current.getSerialUnits() == null ? List.of() : current.getSerialUnits();
        List<PurchaseSerialUnit> incomingSerials = incoming.getSerialUnits() == null ? List.of() : incoming.getSerialUnits();

        if (currentSerials.size() != incomingSerials.size()) {
            throw new PurchaseApiException(422, "SERIAL_COUNT_CHANGE_NOT_ALLOWED",
                    "La cantidad de seriales de una línea existente no puede cambiar en esta versión segura.");
        }

        Set<Long> currentSerialIds = currentSerials.stream()
                .map(PurchaseSerialUnit::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<Long> incomingSerialIds = incomingSerials.stream()
                .map(PurchaseSerialUnit::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (!currentSerialIds.equals(incomingSerialIds)) {
            throw new PurchaseApiException(422, "SERIAL_IDS_MISMATCH",
                    "Para corregir una línea serializada existente debe enviar los mismos seriales con sus IDs.");
        }

        validateSerialRulesForItem(incoming, itemIndex, flags, currentSerialIds);
        assertSerialConflicts(incoming, itemIndex, currentSerialIds);

        incoming.setId(current.getId());
        incoming.setPurchaseId(purchaseId);
        incoming.setProductId(current.getProductId());
        incoming.setLineNumber(current.getLineNumber());
        purchaseRepository.updateItemForEdit(purchaseId, incoming);

        for (PurchaseSerialUnit u : incomingSerials) {
            u.setPurchaseItemId(current.getId());
            u.setProductId(current.getProductId());
            productSerialUnitRepository.updateInboundSerialUnit(u);
        }
    }

    private void removeExistingItem(Long purchaseId,
                                    PurchaseItem current,
                                    String actor,
                                    String editReason,
                                    boolean stockAlreadyLoaded) {
        ProductFlags flags = getFlagsOrThrow(current.getProductId(), -1);
        boolean affectsStock = Boolean.TRUE.equals(flags.getAffectsStock());
        boolean manageBySerial = Boolean.TRUE.equals(flags.getManageBySerial());

        if (manageBySerial) {
            int blocked = productSerialUnitRepository.countBlockedSerialUnitsByPurchaseItemId(current.getId());
            if (blocked > 0) {
                throw new PurchaseApiException(422, "SERIAL_ITEM_ALREADY_USED",
                        "No se puede quitar una línea serializada porque uno o más seriales ya fueron vendidos, reservados, usados en contrato o ventanilla.");
            }
        }

        if (affectsStock && stockAlreadyLoaded) {
            assertStockAvailable(current.getProductId(), current.getQuantity(),
                    "No se puede quitar el ítem porque el stock actual no alcanza para revertir la cantidad ingresada por la compra.");
            stockService.registerOutbound(
                    current.getProductId(),
                    current.getQuantity(),
                    null,
                    "OUT_PURCHASE_EDIT",
                    "purchase_item",
                    current.getId()
            );
        }

        if (manageBySerial) {
            productSerialUnitRepository.markSerialUnitsByPurchaseItemAsBaja(current.getId());
        }

        purchaseRepository.markItemRemoved(purchaseId, current.getId(), actor, editReason);
    }

    private void validateBasicItem(PurchaseItem item, int itemIndex) {
        if (item == null) {
            throw new PurchaseApiException(422, "INVALID_ITEM", "Ítem inválido.");
        }
        if (item.getProductId() == null) {
            throw new PurchaseApiException(422, "INVALID_PRODUCT_ID", "productId es obligatorio.",
                    List.of(PurchaseFieldError.builder()
                            .path(path(itemIndex, "productId"))
                            .message("productId es obligatorio")
                            .build()));
        }
        if (item.getQuantity() == null || item.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new PurchaseApiException(422, "INVALID_QUANTITY", "quantity debe ser mayor que cero.",
                    List.of(PurchaseFieldError.builder()
                            .path(path(itemIndex, "quantity"))
                            .message("quantity debe ser > 0")
                            .value(item.getQuantity())
                            .build()));
        }
        if (item.getUnitCost() == null || item.getUnitCost().compareTo(BigDecimal.ZERO) < 0) {
            throw new PurchaseApiException(422, "INVALID_UNIT_COST", "unitCost no puede ser negativo.",
                    List.of(PurchaseFieldError.builder()
                            .path(path(itemIndex, "unitCost"))
                            .message("unitCost debe ser >= 0")
                            .value(item.getUnitCost())
                            .build()));
        }
    }

    private void validateSerialRulesForItem(PurchaseItem item,
                                            int itemIndex,
                                            ProductFlags flags,
                                            Set<Long> allowedExistingSerialIds) {
        String category = normalizeCategory(flags.getCategory());
        boolean manageBySerial = Boolean.TRUE.equals(flags.getManageBySerial());

        if (manageBySerial && !("MOTOR".equals(category) || "MOTOCICLETAS".equals(category))) {
            throw new PurchaseApiException(422, "SERIAL_PRODUCT_INVALID_CATEGORY",
                    "Solo MOTOR/MOTOCICLETAS pueden ser productos serializados.");
        }

        if (!manageBySerial) {
            if (item.getSerialUnits() != null && !item.getSerialUnits().isEmpty()) {
                throw new PurchaseApiException(422, "SERIAL_UNITS_NOT_ALLOWED",
                        "Este producto no se controla por serie/VIN.");
            }
            return;
        }

        if (!isWholeNumber(item.getQuantity())) {
            throw new PurchaseApiException(422, "SERIAL_QUANTITY_MUST_BE_INTEGER",
                    "Cuando el producto se controla por serie/VIN, quantity debe ser un entero.");
        }

        int expectedUnits = item.getQuantity().intValueExact();
        List<PurchaseSerialUnit> serials = item.getSerialUnits();
        if (serials == null || serials.size() != expectedUnits) {
            throw new PurchaseApiException(422, "SERIAL_UNITS_COUNT_MISMATCH",
                    "La cantidad de serialUnits debe coincidir con quantity.");
        }

        Set<String> vinSeen = new HashSet<>();
        Set<String> chassisSeen = new HashSet<>();
        Set<String> engineSeen = new HashSet<>();
        int currentYear = Year.now().getValue();

        for (int j = 0; j < serials.size(); j++) {
            PurchaseSerialUnit u = serials.get(j);
            if (u == null) {
                throw new PurchaseApiException(422, "INVALID_SERIAL_UNIT", "Unidad serial inválida.");
            }

            if (u.getId() != null && allowedExistingSerialIds != null
                    && (allowedExistingSerialIds.isEmpty() || !allowedExistingSerialIds.contains(u.getId()))) {
                throw new PurchaseApiException(422, "SERIAL_ID_NOT_ALLOWED",
                        "El serial " + u.getId() + " no pertenece al ítem de compra que se está editando o no debe enviarse en una línea nueva.");
            }

            u.setVin(normalizeVehicleIdentifier(u.getVin()));
            u.setChassisNumber(normalizeVehicleIdentifier(u.getChassisNumber()));
            u.setEngineNumber(normalizeVehicleIdentifier(u.getEngineNumber()));
            u.setDuaNumber(normalizeVehicleIdentifier(u.getDuaNumber()));
            u.setColor(normalizeVehicleText(u.getColor()));

            requireText(u.getEngineNumber(), itemIndex, j, "engineNumber");
            requireText(u.getColor(), itemIndex, j, "color");
            requireText(u.getDuaNumber(), itemIndex, j, "duaNumber");
            requirePositiveInteger(u.getDuaItem(), itemIndex, j, "duaItem");

            if (u.getYearMake() == null || u.getYearMake() < 1900 || u.getYearMake() > currentYear + 1) {
                throw new PurchaseApiException(422, "INVALID_YEAR_MAKE", "yearMake inválido.");
            }

            if ("MOTOR".equals(category)) {
                if (notBlank(u.getVin()) || notBlank(u.getChassisNumber())) {
                    throw new PurchaseApiException(422, "MOTOR_VIN_CHASSIS_NOT_ALLOWED",
                            "MOTOR no debe registrar VIN ni chasis.");
                }
            } else {
                requireText(u.getVin(), itemIndex, j, "vin");
                requireText(u.getChassisNumber(), itemIndex, j, "chassisNumber");
            }

            validateVehicleIdentifierField(u.getEngineNumber(), itemIndex, j, "engineNumber");
            validateVehicleIdentifierField(u.getDuaNumber(), itemIndex, j, "duaNumber");
            if (notBlank(u.getVin())) validateVehicleIdentifierField(u.getVin(), itemIndex, j, "vin");
            if (notBlank(u.getChassisNumber())) validateVehicleIdentifierField(u.getChassisNumber(), itemIndex, j, "chassisNumber");

            assertUnique(engineSeen, u.getEngineNumber(), "engineNumber");
            if (notBlank(u.getVin())) assertUnique(vinSeen, u.getVin(), "vin");
            if (notBlank(u.getChassisNumber())) assertUnique(chassisSeen, u.getChassisNumber(), "chassisNumber");
        }
    }

    private void assertSerialConflicts(PurchaseItem item, int itemIndex, Set<Long> excludedSerialIds) {
        Set<String> vins = new HashSet<>();
        Set<String> engines = new HashSet<>();
        Set<String> chassis = new HashSet<>();

        for (PurchaseSerialUnit u : item.getSerialUnits() == null ? List.<PurchaseSerialUnit>of() : item.getSerialUnits()) {
            if (notBlank(u.getVin())) vins.add(u.getVin());
            if (notBlank(u.getEngineNumber())) engines.add(u.getEngineNumber());
            if (notBlank(u.getChassisNumber())) chassis.add(u.getChassisNumber());
        }

        List<SerialIdentifierConflict> conflicts = productSerialUnitRepository.findExistingIdentifiersExcluding(
                vins, engines, chassis, excludedSerialIds == null ? Set.of() : excludedSerialIds
        );

        if (!conflicts.isEmpty()) {
            throw new PurchaseApiException(422, "DUPLICATE_SERIAL_IDENTIFIERS",
                    "Ya existen identificadores seriales registrados en el sistema.",
                    List.of(PurchaseFieldError.builder()
                            .path(path(itemIndex, "serialUnits"))
                            .message("VIN, chasis o número de motor duplicado en base de datos")
                            .build()));
        }
    }

    private ProductFlags getFlagsOrThrow(Long productId, int itemIndex) {
        if (productId == null) {
            throw new PurchaseApiException(422, "INVALID_PRODUCT_ID", "productId es obligatorio.");
        }
        return productFlagsRepository.findById(productId)
                .orElseThrow(() -> new PurchaseApiException(422, "PRODUCT_NOT_FOUND",
                        "No existe producto con id=" + productId,
                        List.of(PurchaseFieldError.builder()
                                .path(itemIndex >= 0 ? path(itemIndex, "productId") : "productId")
                                .message("Producto no encontrado")
                                .value(productId)
                                .build())));
    }

    private void assertStockAvailable(Long productId, BigDecimal quantity, String message) {
        BigDecimal stockOnHand = purchaseRepository.findStockOnHand(productId);
        if (stockOnHand.compareTo(nvl(quantity)) < 0) {
            throw new PurchaseApiException(422, "INSUFFICIENT_STOCK_FOR_PURCHASE_EDIT", message);
        }
    }

    private String toJson(Purchase purchase) {
        try {
            return objectMapper.writeValueAsString(purchase);
        } catch (JsonProcessingException e) {
            throw new PurchaseApiException(500, "PURCHASE_SNAPSHOT_ERROR", "No se pudo generar snapshot de auditoría de compra.");
        }
    }

    private static List<PurchaseItem> safeItems(Purchase purchase) {
        return purchase == null || purchase.getItems() == null ? List.of() : purchase.getItems();
    }

    private static String resolveActor(String username) {
        return (username == null || username.isBlank()) ? "SYSTEM" : username.trim();
    }

    private static String path(int itemIndex, String field) {
        return itemIndex >= 0 ? "items[" + itemIndex + "]." + field : field;
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static boolean hasChangedMoney(BigDecimal a, BigDecimal b) {
        return nvl(a).compareTo(nvl(b)) != 0;
    }

    private static boolean isWholeNumber(BigDecimal value) {
        if (value == null) return false;
        return value.stripTrailingZeros().scale() <= 0;
    }

    private static String normalizeText(String s) {
        if (s == null) return null;
        String t = s.trim().replaceAll("\\s+", " ");
        return t.isEmpty() ? null : t;
    }

    private static String normalizeCategory(String s) {
        if (s == null) return null;
        String t = s.trim().toUpperCase(Locale.ROOT);
        if (t.equals("MOTO") || t.equals("MOTOCICLETA") || t.equals("MOTOCICLETAS")) return "MOTOCICLETAS";
        if (t.equals("MOTORES") || t.equals("MOTOR")) return "MOTOR";
        return t.isEmpty() ? null : t;
    }

    private static String normalizeVehicleIdentifier(String s) {
        if (s == null) return null;
        String t = s.trim().toUpperCase(Locale.ROOT);
        if (t.isEmpty()) return null;
        t = t.replace('–', '-').replace('—', '-').replace('−', '-');
        t = t.replaceAll("\\s+", " ");
        return t.isEmpty() ? null : t;
    }

    private static String normalizeVehicleText(String s) {
        if (s == null) return null;
        String t = s.trim().toUpperCase(Locale.ROOT);
        if (t.isEmpty()) return null;
        t = t.replace('–', '-').replace('—', '-').replace('−', '-');
        t = t.replaceAll("\\s+", " ");
        return t.isEmpty() ? null : t;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static void requireText(String value, int itemIndex, int serialIndex, String field) {
        if (!notBlank(value)) {
            throw new PurchaseApiException(422, "REQUIRED_FIELD", field + " es obligatorio.",
                    List.of(PurchaseFieldError.builder()
                            .path(path(itemIndex, "serialUnits[" + serialIndex + "]." + field))
                            .message(field + " es obligatorio")
                            .build()));
        }
    }

    private static void requirePositiveInteger(Integer value, int itemIndex, int serialIndex, String field) {
        if (value == null || value <= 0) {
            throw new PurchaseApiException(422, "REQUIRED_FIELD", field + " es obligatorio y debe ser > 0.",
                    List.of(PurchaseFieldError.builder()
                            .path(path(itemIndex, "serialUnits[" + serialIndex + "]." + field))
                            .message(field + " es obligatorio y debe ser > 0")
                            .value(value)
                            .expected("> 0")
                            .build()));
        }
    }

    private static void validateVehicleIdentifierField(String value,
                                                       int itemIndex,
                                                       int serialIndex,
                                                       String field) {
        if (!notBlank(value)) return;
        if (value.length() > 80) {
            throw new PurchaseApiException(422, "INVALID_FIELD_LENGTH", field + " inválido.",
                    List.of(PurchaseFieldError.builder()
                            .path(path(itemIndex, "serialUnits[" + serialIndex + "]." + field))
                            .message(field + " debe tener máximo 80 caracteres")
                            .value(value)
                            .build()));
        }
        if (!VEHICLE_IDENTIFIER_PATTERN.matcher(value).matches()) {
            throw new PurchaseApiException(422, "INVALID_FIELD_FORMAT", field + " inválido.",
                    List.of(PurchaseFieldError.builder()
                            .path(path(itemIndex, "serialUnits[" + serialIndex + "]." + field))
                            .message(field + " solo puede contener letras, números, espacios internos y separadores comunes (- / . * _ # +)")
                            .value(value)
                            .build()));
        }
    }

    private static void assertUnique(Set<String> seen, String value, String field) {
        if (!notBlank(value)) return;
        if (!seen.add(value)) {
            throw new PurchaseApiException(422, "DUPLICATE_SERIAL_IDENTIFIERS",
                    "Existen identificadores duplicados dentro del request: " + field + "=" + value);
        }
    }
}
