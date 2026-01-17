package com.paulfernandosr.possystembackend.purchase.application;

import com.paulfernandosr.possystembackend.purchase.domain.Purchase;
import com.paulfernandosr.possystembackend.purchase.domain.PurchaseItem;
import com.paulfernandosr.possystembackend.purchase.domain.PurchaseSerialUnit;
import com.paulfernandosr.possystembackend.purchase.domain.exception.PurchaseApiException;
import com.paulfernandosr.possystembackend.purchase.domain.exception.PurchaseFieldError;
import com.paulfernandosr.possystembackend.purchase.domain.model.ProductFlags;
import com.paulfernandosr.possystembackend.purchase.domain.model.SerialIdentifierConflict;
import com.paulfernandosr.possystembackend.purchase.domain.port.input.CreatePurchaseUseCase;
import com.paulfernandosr.possystembackend.purchase.domain.port.output.ProductFlagsRepository;
import com.paulfernandosr.possystembackend.purchase.domain.port.output.ProductSerialUnitRepository;
import com.paulfernandosr.possystembackend.purchase.domain.port.output.PurchaseRepository;
import com.paulfernandosr.possystembackend.stock.domain.port.input.StockService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Year;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class RegisterPurchaseWithStockService implements CreatePurchaseUseCase {

    private static final Pattern ID_PATTERN = Pattern.compile("^[A-Z0-9-]+$");

    private final PurchaseRepository purchaseRepository;
    private final StockService stockService;
    private final ProductFlagsRepository productFlagsRepository;
    private final ProductSerialUnitRepository productSerialUnitRepository;

    public RegisterPurchaseWithStockService(PurchaseRepository purchaseRepository,
                                            StockService stockService,
                                            ProductFlagsRepository productFlagsRepository,
                                            ProductSerialUnitRepository productSerialUnitRepository) {
        this.purchaseRepository = purchaseRepository;
        this.stockService = stockService;
        this.productFlagsRepository = productFlagsRepository;
        this.productSerialUnitRepository = productSerialUnitRepository;
    }

    @Override
    @Transactional
    public Purchase createPurchase(Purchase purchase) {

        validateDeliveryGuide(purchase);

        ValidationContext ctx = validateItemsAndSerialRules(purchase);

        // 1.3 Validación de unicidad en BD (por lote)
        List<SerialIdentifierConflict> conflicts = productSerialUnitRepository.findExistingIdentifiers(
                ctx.allVins, ctx.allEngineNumbers, ctx.allSerialNumbers
        );

        if (!conflicts.isEmpty()) {
            throw buildDbConflictException(conflicts, ctx);
        }

        // Insert cabecera + items (items ya vuelven con ID por RETURNING)
        Purchase created = purchaseRepository.create(purchase);

        // Insert serial units + stock
        if (created.getItems() != null) {
            for (int i = 0; i < created.getItems().size(); i++) {
                PurchaseItem item = created.getItems().get(i);
                ProductFlags flags = ctx.flagsByIndex.get(i);

                boolean affectsStock = Boolean.TRUE.equals(flags.getAffectsStock());
                boolean manageBySerial = Boolean.TRUE.equals(flags.getManageBySerial());

                if (manageBySerial) {
                    // Insert serial units vinculadas al purchase_item
                    try {
                        productSerialUnitRepository.insertInboundSerialUnits(
                                item.getId(),
                                item.getProductId(),
                                item.getSerialUnits()
                        );
                    } catch (DuplicateKeyException ex) {
                        // Race condition: índice único saltó entre la validación y el insert
                        throw new PurchaseApiException(
                                409,
                                "DUPLICATE_SERIAL_IDENTIFIER",
                                "Ya existe una unidad serializada con alguno de los identificadores ingresados."
                        );
                    }
                }

                // Stock/kardex solo si affectsStock=true
                if (affectsStock) {
                    BigDecimal qty = item.getQuantity();
                    BigDecimal unitCost = item.getTotalCost() != null && item.getQuantity() != null
                            && item.getQuantity().compareTo(BigDecimal.ZERO) > 0
                            ? item.getTotalCost().divide(item.getQuantity(), 6, RoundingMode.HALF_UP)
                            : item.getUnitCost();

                    stockService.registerInbound(
                            item.getProductId(),
                            qty,
                            unitCost,
                            "IN_PURCHASE",
                            "purchase_item",
                            item.getId()
                    );
                }
            }
        }

        return created;
    }

    private void validateDeliveryGuide(Purchase purchase) {
        boolean hasAny =
                notBlank(purchase.getDeliveryGuideSeries()) ||
                        notBlank(purchase.getDeliveryGuideNumber()) ||
                        notBlank(purchase.getDeliveryGuideCompany());

        if (hasAny) {
            if (isBlank(purchase.getDeliveryGuideSeries()) ||
                    isBlank(purchase.getDeliveryGuideNumber()) ||
                    isBlank(purchase.getDeliveryGuideCompany())) {
                throw new PurchaseApiException(
                        422,
                        "INVALID_DELIVERY_GUIDE",
                        "La guía de remisión debe incluir serie, número y empresa.",
                        List.of(
                                PurchaseFieldError.builder().path("deliveryGuideSeries").message("Requerido").build(),
                                PurchaseFieldError.builder().path("deliveryGuideNumber").message("Requerido").build(),
                                PurchaseFieldError.builder().path("deliveryGuideCompany").message("Requerido").build()
                        )
                );
            }
        }
    }

    private ValidationContext validateItemsAndSerialRules(Purchase purchase) {
        if (purchase.getItems() == null || purchase.getItems().isEmpty()) {
            throw new PurchaseApiException(422, "ITEMS_REQUIRED", "La compra debe incluir items.",
                    List.of(PurchaseFieldError.builder().path("items").message("Requerido").build()));
        }

        ValidationContext ctx = new ValidationContext(purchase.getItems().size());

        for (int i = 0; i < purchase.getItems().size(); i++) {
            final int idx = i;
            final PurchaseItem item = purchase.getItems().get(i);

            if (item.getProductId() == null) {
                throw new PurchaseApiException(422, "PRODUCT_ID_REQUIRED", "Cada item debe incluir productId.",
                        List.of(PurchaseFieldError.builder().path(path(i, "productId")).message("Requerido").build()));
            }

            ProductFlags flags = productFlagsRepository.findById(item.getProductId())
                    .orElseThrow(() -> new PurchaseApiException(
                            422, "PRODUCT_NOT_FOUND", "No existe el producto indicado.",
                            List.of(PurchaseFieldError.builder()
                                    .path(path(idx, "productId"))
                                    .message("Producto no encontrado")
                                    .value(item.getProductId())
                                    .build())
                    ));

            ctx.flagsByIndex.put(i, flags);

            boolean manageBySerial = Boolean.TRUE.equals(flags.getManageBySerial());
            boolean affectsStock = Boolean.TRUE.equals(flags.getAffectsStock());

            // Seguridad de negocio: no se admite serializable que no afecte stock
            if (manageBySerial && !affectsStock) {
                throw new PurchaseApiException(422, "SERIAL_PRODUCT_MUST_AFFECT_STOCK",
                        "Un producto con manageBySerial=true debe afectar stock (affectsStock=true).",
                        List.of(PurchaseFieldError.builder().path(path(i, "productId")).value(item.getProductId()).build()));
            }

            if (!manageBySerial) {
                if (item.getSerialUnits() != null && !item.getSerialUnits().isEmpty()) {
                    throw new PurchaseApiException(422, "SERIAL_UNITS_NOT_ALLOWED_FOR_NON_SERIAL_PRODUCT",
                            "No se permiten serialUnits para productos sin control por serie.",
                            List.of(PurchaseFieldError.builder()
                                    .path(path(i, "serialUnits"))
                                    .message("No permitido")
                                    .build()));
                }
                continue;
            }

            int qtyInt = parseQuantityAsInt(item.getQuantity(), i);

            if (item.getSerialUnits() == null || item.getSerialUnits().isEmpty()) {
                throw new PurchaseApiException(422, "SERIAL_UNITS_REQUIRED",
                        "Producto requiere detalle por unidad (manageBySerial=true).",
                        List.of(PurchaseFieldError.builder().path(path(i, "serialUnits")).message("Requerido").build()));
            }

            if (item.getSerialUnits().size() != qtyInt) {
                throw new PurchaseApiException(422, "SERIAL_UNITS_COUNT_MISMATCH",
                        "La cantidad de serialUnits debe coincidir con quantity.",
                        List.of(PurchaseFieldError.builder()
                                .path(path(i, "serialUnits"))
                                .message("quantity debe ser igual a serialUnits.length")
                                .expected(qtyInt)
                                .value(item.getSerialUnits().size())
                                .build()));
            }

            normalizeAndValidateSerialUnits(item, i, ctx);
            validateNoDuplicatesInsideRequest(item, i);
        }

        return ctx;
    }

    private int parseQuantityAsInt(BigDecimal quantity, int itemIndex) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PurchaseApiException(422, "QUANTITY_REQUIRED",
                    "quantity debe ser mayor a 0.",
                    List.of(PurchaseFieldError.builder().path(path(itemIndex, "quantity")).message("Inválido").build()));
        }
        try {
            return quantity.stripTrailingZeros().intValueExact();
        } catch (ArithmeticException ex) {
            throw new PurchaseApiException(422, "QUANTITY_MUST_BE_INTEGER_FOR_SERIAL",
                    "Para productos serializados, quantity debe ser entero.",
                    List.of(PurchaseFieldError.builder().path(path(itemIndex, "quantity")).message("Debe ser entero").build()));
        }
    }

    private void normalizeAndValidateSerialUnits(PurchaseItem item, int itemIndex, ValidationContext ctx) {
        int currentYearPlus1 = Year.now().getValue() + 1;

        for (int j = 0; j < item.getSerialUnits().size(); j++) {
            PurchaseSerialUnit u = item.getSerialUnits().get(j);

            u.setVin(normId(u.getVin()));
            u.setEngineNumber(normId(u.getEngineNumber()));
            u.setSerialNumber(normId(u.getSerialNumber()));
            u.setColor(normText(u.getColor()));
            u.setVehicleClass(normText(u.getVehicleClass()));
            u.setLocationCode(normText(u.getLocationCode()));

            // Requeridos mínimos
            if (isBlank(u.getEngineNumber())) {
                throw new PurchaseApiException(422, "ENGINE_NUMBER_REQUIRED",
                        "engineNumber es requerido.",
                        List.of(PurchaseFieldError.builder()
                                .path(path(itemIndex, "serialUnits[" + j + "].engineNumber"))
                                .message("Requerido")
                                .build()));
            }
            if (isBlank(u.getLocationCode())) {
                throw new PurchaseApiException(422, "LOCATION_CODE_REQUIRED",
                        "locationCode es requerido.",
                        List.of(PurchaseFieldError.builder()
                                .path(path(itemIndex, "serialUnits[" + j + "].locationCode"))
                                .message("Requerido")
                                .build()));
            }

            // Regex y longitudes
            validateIdField(u.getVin(), 8, 32, itemIndex, j, "vin");
            validateIdField(u.getEngineNumber(), 4, 40, itemIndex, j, "engineNumber");
            validateIdField(u.getSerialNumber(), 1, 40, itemIndex, j, "serialNumber");

            validateTextField(u.getColor(), 1, 30, itemIndex, j, "color");
            validateTextField(u.getLocationCode(), 1, 30, itemIndex, j, "locationCode");

            if (u.getYearMake() != null && (u.getYearMake() < 1980 || u.getYearMake() > currentYearPlus1)) {
                throw new PurchaseApiException(422, "INVALID_YEAR_MAKE", "yearMake inválido.",
                        List.of(PurchaseFieldError.builder()
                                .path(path(itemIndex, "serialUnits[" + j + "].yearMake"))
                                .value(u.getYearMake())
                                .expected("1980.." + currentYearPlus1)
                                .build()));
            }
            if (u.getYearModel() != null && (u.getYearModel() < 1980 || u.getYearModel() > currentYearPlus1)) {
                throw new PurchaseApiException(422, "INVALID_YEAR_MODEL", "yearModel inválido.",
                        List.of(PurchaseFieldError.builder()
                                .path(path(itemIndex, "serialUnits[" + j + "].yearModel"))
                                .value(u.getYearModel())
                                .expected("1980.." + currentYearPlus1)
                                .build()));
            }

            // Registrar para validación BD (por lote) + paths para detalle de conflictos
            if (notBlank(u.getVin())) {
                ctx.allVins.add(u.getVin());
                ctx.vinPaths.computeIfAbsent(u.getVin(), k -> new ArrayList<>())
                        .add(path(itemIndex, "serialUnits[" + j + "].vin"));
            }
            if (notBlank(u.getEngineNumber())) {
                ctx.allEngineNumbers.add(u.getEngineNumber());
                ctx.enginePaths.computeIfAbsent(u.getEngineNumber(), k -> new ArrayList<>())
                        .add(path(itemIndex, "serialUnits[" + j + "].engineNumber"));
            }
            if (notBlank(u.getSerialNumber())) {
                ctx.allSerialNumbers.add(u.getSerialNumber());
                ctx.serialPaths.computeIfAbsent(u.getSerialNumber(), k -> new ArrayList<>())
                        .add(path(itemIndex, "serialUnits[" + j + "].serialNumber"));
            }
        }
    }

    private void validateNoDuplicatesInsideRequest(PurchaseItem item, int itemIndex) {
        Set<String> vins = new HashSet<>();
        Set<String> engines = new HashSet<>();
        Set<String> serials = new HashSet<>();

        List<PurchaseFieldError> errors = new ArrayList<>();

        for (int j = 0; j < item.getSerialUnits().size(); j++) {
            PurchaseSerialUnit u = item.getSerialUnits().get(j);

            if (notBlank(u.getVin()) && !vins.add(u.getVin())) {
                errors.add(PurchaseFieldError.builder()
                        .path(path(itemIndex, "serialUnits[" + j + "].vin"))
                        .message("VIN duplicado en el request")
                        .value(u.getVin())
                        .build());
            }
            if (notBlank(u.getEngineNumber()) && !engines.add(u.getEngineNumber())) {
                errors.add(PurchaseFieldError.builder()
                        .path(path(itemIndex, "serialUnits[" + j + "].engineNumber"))
                        .message("Engine number duplicado en el request")
                        .value(u.getEngineNumber())
                        .build());
            }
            if (notBlank(u.getSerialNumber()) && !serials.add(u.getSerialNumber())) {
                errors.add(PurchaseFieldError.builder()
                        .path(path(itemIndex, "serialUnits[" + j + "].serialNumber"))
                        .message("Serial number duplicado en el request")
                        .value(u.getSerialNumber())
                        .build());
            }
        }

        if (!errors.isEmpty()) {
            throw new PurchaseApiException(422, "SERIAL_UNITS_DUPLICATED_IN_REQUEST",
                    "Existen seriales duplicados dentro del request.", errors);
        }
    }

    private PurchaseApiException buildDbConflictException(List<SerialIdentifierConflict> conflicts, ValidationContext ctx) {
        List<PurchaseFieldError> errors = new ArrayList<>();
        boolean hasEngine = false;
        boolean hasVin = false;
        boolean hasSerial = false;

        for (SerialIdentifierConflict c : conflicts) {
            if (notBlank(c.getEngineNumber()) && ctx.enginePaths.containsKey(c.getEngineNumber())) {
                hasEngine = true;
                for (String p : ctx.enginePaths.get(c.getEngineNumber())) {
                    errors.add(PurchaseFieldError.builder()
                            .path(p).message("Engine number ya existe en BD").value(c.getEngineNumber()).build());
                }
            }
            if (notBlank(c.getVin()) && ctx.vinPaths.containsKey(c.getVin())) {
                hasVin = true;
                for (String p : ctx.vinPaths.get(c.getVin())) {
                    errors.add(PurchaseFieldError.builder()
                            .path(p).message("VIN ya existe en BD").value(c.getVin()).build());
                }
            }
            if (notBlank(c.getSerialNumber()) && ctx.serialPaths.containsKey(c.getSerialNumber())) {
                hasSerial = true;
                for (String p : ctx.serialPaths.get(c.getSerialNumber())) {
                    errors.add(PurchaseFieldError.builder()
                            .path(p).message("Serial number ya existe en BD").value(c.getSerialNumber()).build());
                }
            }
        }

        String code;
        String msg;
        if (hasEngine) { code = "DUPLICATE_ENGINE_NUMBER"; msg = "Ya existe un engineNumber registrado."; }
        else if (hasVin) { code = "DUPLICATE_VIN"; msg = "Ya existe un VIN registrado."; }
        else if (hasSerial) { code = "DUPLICATE_SERIAL_NUMBER"; msg = "Ya existe un serialNumber registrado."; }
        else { code = "DUPLICATE_SERIAL_IDENTIFIER"; msg = "Ya existe un identificador serializado registrado."; }

        return new PurchaseApiException(409, code, msg, errors);
    }

    private void validateIdField(String value, int min, int max, int itemIndex, int unitIndex, String field) {
        if (isBlank(value)) return;
        if (value.length() < min || value.length() > max) {
            throw new PurchaseApiException(422, "INVALID_" + field.toUpperCase(), field + " longitud inválida.",
                    List.of(PurchaseFieldError.builder()
                            .path(path(itemIndex, "serialUnits[" + unitIndex + "]." + field))
                            .value(value)
                            .expected(min + ".." + max)
                            .build()));
        }
        if (!ID_PATTERN.matcher(value).matches()) {
            throw new PurchaseApiException(422, "INVALID_" + field.toUpperCase(), field + " formato inválido.",
                    List.of(PurchaseFieldError.builder()
                            .path(path(itemIndex, "serialUnits[" + unitIndex + "]." + field))
                            .value(value)
                            .expected("A-Z0-9-")
                            .build()));
        }
    }

    private void validateTextField(String value, int min, int max, int itemIndex, int unitIndex, String field) {
        if (isBlank(value)) return;
        if (value.length() < min || value.length() > max) {
            throw new PurchaseApiException(422, "INVALID_" + field.toUpperCase(), field + " longitud inválida.",
                    List.of(PurchaseFieldError.builder()
                            .path(path(itemIndex, "serialUnits[" + unitIndex + "]." + field))
                            .value(value)
                            .expected(min + ".." + max)
                            .build()));
        }
    }

    private static String path(int itemIndex, String tail) {
        return "items[" + itemIndex + "]." + tail;
    }

    private static String normId(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        return t.toUpperCase();
    }

    private static String normText(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
    private static boolean notBlank(String s) { return !isBlank(s); }

    private static class ValidationContext {
        final Map<Integer, ProductFlags> flagsByIndex = new HashMap<>();
        final Set<String> allVins = new HashSet<>();
        final Set<String> allEngineNumbers = new HashSet<>();
        final Set<String> allSerialNumbers = new HashSet<>();
        final Map<String, List<String>> vinPaths = new HashMap<>();
        final Map<String, List<String>> enginePaths = new HashMap<>();
        final Map<String, List<String>> serialPaths = new HashMap<>();
        ValidationContext(int itemCount) {}
    }
}
