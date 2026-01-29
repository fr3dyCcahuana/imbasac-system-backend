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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Year;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class RegisterPurchaseWithStockService implements CreatePurchaseUseCase {

    private static final Pattern ID_PATTERN = Pattern.compile("^[A-Z0-9-]+$");

    private final PurchaseRepository purchaseRepository;
    private final ProductFlagsRepository productFlagsRepository;
    private final ProductSerialUnitRepository productSerialUnitRepository;
    private final StockService stockService;

    @Override
    @Transactional
    public Purchase createPurchase(Purchase purchase) {
        if (purchase == null) {
            throw new PurchaseApiException(422, "INVALID_PURCHASE", "La compra es obligatoria.");
        }
        if (purchase.getItems() == null || purchase.getItems().isEmpty()) {
            throw new PurchaseApiException(422, "INVALID_PURCHASE_ITEMS", "La compra debe tener al menos un ítem.");
        }

        ValidationContext ctx = new ValidationContext();
        validateItemsAndSerialRules(purchase, ctx);

        // 1) Validar contra base de datos antes de insertar (evita 500 por índices UNIQUE)
        List<SerialIdentifierConflict> conflicts = productSerialUnitRepository.findExistingIdentifiers(
                ctx.allVins,
                ctx.allEngineNumbers,
                ctx.allChassisNumbers
        );
        if (!conflicts.isEmpty()) {
            throw buildDbConflictException(conflicts, ctx);
        }

        // 2) Insertar compra + detalle (debe retornar IDs de purchase_item)
        Purchase created = purchaseRepository.create(purchase);

        // 3) Registrar stock + seriales (si aplica)
        for (PurchaseItem item : created.getItems()) {
            ProductFlags flags = ctx.flagsByProductId.get(item.getProductId());
            if (flags == null) continue;

            boolean affectsStock = Boolean.TRUE.equals(flags.getAffectsStock());
            boolean manageBySerial = Boolean.TRUE.equals(flags.getManageBySerial());

            if (!affectsStock) {
                continue; // servicios u otros que no afectan stock
            }

            // Movimiento IN del kardex (para todos los que afectan stock)
            stockService.registerInbound(
                    item.getProductId(),
                    item.getQuantity(),
                    item.getUnitCost(),
                    "IN_PURCHASE",
                    "purchase_item",
                    item.getId()
            );

            // Seriales: insertar unidades físicas por item
            if (manageBySerial) {
                productSerialUnitRepository.insertInboundSerialUnits(
                        item.getId(),
                        item.getProductId(),
                        item.getSerialUnits()
                );
            }
        }

        return created;
    }

    // ---------------------------------------------------------------------
    // VALIDACIONES
    // ---------------------------------------------------------------------

    private void validateItemsAndSerialRules(Purchase purchase, ValidationContext ctx) {

        for (int i = 0; i < purchase.getItems().size(); i++) {
            PurchaseItem item = purchase.getItems().get(i);
            if (item == null) {
                throw new PurchaseApiException(422, "INVALID_ITEM", "Ítem inválido.",
                        List.of(PurchaseFieldError.builder()
                                .path(path(i, "item"))
                                .message("El ítem no puede ser null")
                                .build()));
            }

            if (item.getProductId() == null) {
                throw new PurchaseApiException(422, "INVALID_PRODUCT_ID", "productId es obligatorio.",
                        List.of(PurchaseFieldError.builder()
                                .path(path(i, "productId"))
                                .message("productId es obligatorio")
                                .build()));
            }

            int finalI = i;
            ProductFlags flags = productFlagsRepository.findById(item.getProductId())
                    .orElseThrow(() -> new PurchaseApiException(
                            422,
                            "PRODUCT_NOT_FOUND",
                            "No existe producto con id=" + item.getProductId(),
                            List.of(PurchaseFieldError.builder()
                                    .path(path(finalI, "productId"))
                                    .message("Producto no encontrado")
                                    .value(item.getProductId())
                                    .build())
                    ));

            ctx.flagsByProductId.put(item.getProductId(), flags);

            String category = normalizeCategory(flags.getCategory());
            boolean manageBySerial = Boolean.TRUE.equals(flags.getManageBySerial());

            if (manageBySerial && !isMotorOrMoto(category)) {
                throw new PurchaseApiException(
                        422,
                        "SERIAL_PRODUCT_INVALID_CATEGORY",
                        "Solo productos con categoría MOTOR o MOTOCICLETAS pueden tener manage_by_serial=true.",
                        List.of(PurchaseFieldError.builder()
                                .path(path(i, "productId"))
                                .message("Producto serializado con categoría inválida: " + flags.getCategory())
                                .value(flags.getCategory())
                                .expected("MOTOR/MOTOCICLETAS")
                                .build())
                );
            }

            if (!manageBySerial) {
                // No seriales: no debe traer serialUnits
                if (item.getSerialUnits() != null && !item.getSerialUnits().isEmpty()) {
                    throw new PurchaseApiException(
                            422,
                            "SERIAL_UNITS_NOT_ALLOWED",
                            "Este producto no se controla por serie/VIN.",
                            List.of(PurchaseFieldError.builder()
                                    .path(path(i, "serialUnits"))
                                    .message("serialUnits no permitido cuando manage_by_serial=false")
                                    .build())
                    );
                }
                continue;
            }

            // Serializables: quantity debe ser entero (n unidades físicas)
            if (item.getQuantity() == null || item.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new PurchaseApiException(422, "INVALID_QUANTITY", "quantity inválido.",
                        List.of(PurchaseFieldError.builder()
                                .path(path(i, "quantity"))
                                .message("quantity debe ser > 0")
                                .value(item.getQuantity())
                                .build()));
            }
            if (!isWholeNumber(item.getQuantity())) {
                throw new PurchaseApiException(422, "SERIAL_QUANTITY_MUST_BE_INTEGER",
                        "Cuando el producto se controla por serie/VIN, quantity debe ser un entero.",
                        List.of(PurchaseFieldError.builder()
                                .path(path(i, "quantity"))
                                .message("quantity debe ser entero para productos serializados")
                                .value(item.getQuantity())
                                .expected("Entero")
                                .build()));
            }

            int expectedUnits = item.getQuantity().intValueExact();

            if (item.getSerialUnits() == null || item.getSerialUnits().isEmpty()) {
                throw new PurchaseApiException(422, "SERIAL_UNITS_REQUIRED",
                        "Debe enviar serialUnits para productos serializados.",
                        List.of(PurchaseFieldError.builder()
                                .path(path(i, "serialUnits"))
                                .message("serialUnits es obligatorio")
                                .expected("Tamaño=" + expectedUnits)
                                .build()));
            }

            if (item.getSerialUnits().size() != expectedUnits) {
                throw new PurchaseApiException(422, "SERIAL_UNITS_COUNT_MISMATCH",
                        "La cantidad de serialUnits debe coincidir con quantity.",
                        List.of(PurchaseFieldError.builder()
                                .path(path(i, "serialUnits"))
                                .message("serialUnits.size() debe ser igual a quantity")
                                .value(item.getSerialUnits().size())
                                .expected(expectedUnits)
                                .build()));
            }

            normalizeAndValidateSerialUnits(item, i, category, ctx);
            validateNoDuplicatesInsideRequest(item, i, ctx);
        }
    }

    private void normalizeAndValidateSerialUnits(PurchaseItem item,
                                                 int itemIndex,
                                                 String category,
                                                 ValidationContext ctx) {
        int currentYear = Year.now().getValue();

        for (int j = 0; j < item.getSerialUnits().size(); j++) {
            PurchaseSerialUnit u = item.getSerialUnits().get(j);
            if (u == null) {
                throw new PurchaseApiException(422, "INVALID_SERIAL_UNIT", "Unidad serial inválida.",
                        List.of(PurchaseFieldError.builder()
                                .path(path(itemIndex, "serialUnits[" + j + "]"))
                                .message("La unidad no puede ser null")
                                .build()));
            }

            // Normaliza
            u.setVin(normalizeId(u.getVin()));
            u.setChassisNumber(normalizeId(u.getChassisNumber()));
            u.setEngineNumber(normalizeId(u.getEngineNumber()));
            u.setDuaNumber(normalizeId(u.getDuaNumber()));
            u.setColor(normalizeText(u.getColor()));

            // Comunes obligatorios
            requireText(u.getColor(), itemIndex, j, "color");
            requireText(u.getEngineNumber(), itemIndex, j, "engineNumber");
            requireText(u.getDuaNumber(), itemIndex, j, "duaNumber");
            requirePositiveInteger(u.getDuaItem(), itemIndex, j, "duaItem");

            if (u.getYearMake() == null || u.getYearMake() < 1900 || u.getYearMake() > currentYear + 1) {
                throw new PurchaseApiException(422, "INVALID_YEAR_MAKE", "yearMake inválido.",
                        List.of(PurchaseFieldError.builder()
                                .path(path(itemIndex, "serialUnits[" + j + "].yearMake"))
                                .message("yearMake debe ser válido")
                                .value(u.getYearMake())
                                .expected("1900.." + (currentYear + 1))
                                .build()));
            }

            // Por categoría
            if ("MOTOR".equals(category)) {
                if (notBlank(u.getVin())) {
                    throw new PurchaseApiException(422, "VIN_NOT_ALLOWED_FOR_MOTOR",
                            "MOTOR no debe registrar VIN.",
                            List.of(PurchaseFieldError.builder()
                                    .path(path(itemIndex, "serialUnits[" + j + "].vin"))
                                    .message("vin no permitido para categoría MOTOR")
                                    .value(u.getVin())
                                    .expected(null)
                                    .build()));
                }
                if (notBlank(u.getChassisNumber())) {
                    throw new PurchaseApiException(422, "CHASSIS_NOT_ALLOWED_FOR_MOTOR",
                            "MOTOR no debe registrar número de chasis.",
                            List.of(PurchaseFieldError.builder()
                                    .path(path(itemIndex, "serialUnits[" + j + "].chassisNumber"))
                                    .message("chassisNumber no permitido para categoría MOTOR")
                                    .value(u.getChassisNumber())
                                    .expected(null)
                                    .build()));
                }
            } else {
                // MOTOCICLETAS
                requireText(u.getVin(), itemIndex, j, "vin");
                requireText(u.getChassisNumber(), itemIndex, j, "chassisNumber");
            }

            // Validación de formatos
            validateIdField(u.getEngineNumber(), 4, 40, itemIndex, j, "engineNumber");
            validateIdField(u.getDuaNumber(), 1, 30, itemIndex, j, "duaNumber");

            if (notBlank(u.getVin())) {
                validateIdField(u.getVin(), 8, 40, itemIndex, j, "vin");
            }
            if (notBlank(u.getChassisNumber())) {
                validateIdField(u.getChassisNumber(), 1, 40, itemIndex, j, "chassisNumber");
            }

            // Acumula para validación de duplicados en BD
            ctx.allEngineNumbers.add(u.getEngineNumber());
            ctx.enginePaths.computeIfAbsent(u.getEngineNumber(), k -> new ArrayList<>())
                    .add(path(itemIndex, "serialUnits[" + j + "].engineNumber"));

            if (notBlank(u.getVin())) {
                ctx.allVins.add(u.getVin());
                ctx.vinPaths.computeIfAbsent(u.getVin(), k -> new ArrayList<>())
                        .add(path(itemIndex, "serialUnits[" + j + "].vin"));
            }
            if (notBlank(u.getChassisNumber())) {
                ctx.allChassisNumbers.add(u.getChassisNumber());
                ctx.chassisPaths.computeIfAbsent(u.getChassisNumber(), k -> new ArrayList<>())
                        .add(path(itemIndex, "serialUnits[" + j + "].chassisNumber"));
            }
        }
    }

    private void validateNoDuplicatesInsideRequest(PurchaseItem item, int itemIndex, ValidationContext ctx) {

        Map<String, Integer> engineCount = new HashMap<>();
        Map<String, Integer> vinCount = new HashMap<>();
        Map<String, Integer> chassisCount = new HashMap<>();

        for (int j = 0; j < item.getSerialUnits().size(); j++) {
            PurchaseSerialUnit u = item.getSerialUnits().get(j);
            if (u == null) continue;

            if (notBlank(u.getEngineNumber())) {
                engineCount.merge(u.getEngineNumber(), 1, Integer::sum);
            }
            if (notBlank(u.getVin())) {
                vinCount.merge(u.getVin(), 1, Integer::sum);
            }
            if (notBlank(u.getChassisNumber())) {
                chassisCount.merge(u.getChassisNumber(), 1, Integer::sum);
            }
        }

        List<PurchaseFieldError> errors = new ArrayList<>();

        engineCount.forEach((k, v) -> {
            if (v > 1) {
                List<String> paths = ctx.enginePaths.getOrDefault(k, List.of(path(itemIndex, "serialUnits.engineNumber")));
                for (String p : paths) {
                    errors.add(PurchaseFieldError.builder()
                            .path(p)
                            .message("engineNumber duplicado en el request")
                            .value(k)
                            .build());
                }
            }
        });

        vinCount.forEach((k, v) -> {
            if (v > 1) {
                List<String> paths = ctx.vinPaths.getOrDefault(k, List.of(path(itemIndex, "serialUnits.vin")));
                for (String p : paths) {
                    errors.add(PurchaseFieldError.builder()
                            .path(p)
                            .message("vin duplicado en el request")
                            .value(k)
                            .build());
                }
            }
        });

        chassisCount.forEach((k, v) -> {
            if (v > 1) {
                List<String> paths = ctx.chassisPaths.getOrDefault(k, List.of(path(itemIndex, "serialUnits.chassisNumber")));
                for (String p : paths) {
                    errors.add(PurchaseFieldError.builder()
                            .path(p)
                            .message("chassisNumber duplicado en el request")
                            .value(k)
                            .build());
                }
            }
        });

        if (!errors.isEmpty()) {
            throw new PurchaseApiException(422, "DUPLICATE_SERIAL_IDENTIFIERS",
                    "Existen identificadores duplicados dentro del request.",
                    errors);
        }
    }

    private PurchaseApiException buildDbConflictException(List<SerialIdentifierConflict> conflicts, ValidationContext ctx) {
        List<PurchaseFieldError> errors = new ArrayList<>();

        for (SerialIdentifierConflict c : conflicts) {
            if (notBlank(c.getEngineNumber()) && ctx.enginePaths.containsKey(c.getEngineNumber())) {
                for (String p : ctx.enginePaths.get(c.getEngineNumber())) {
                    errors.add(PurchaseFieldError.builder()
                            .path(p)
                            .message("Ya existe un engineNumber registrado.")
                            .value(c.getEngineNumber())
                            .build());
                }
            }

            if (notBlank(c.getVin()) && ctx.vinPaths.containsKey(c.getVin())) {
                for (String p : ctx.vinPaths.get(c.getVin())) {
                    errors.add(PurchaseFieldError.builder()
                            .path(p)
                            .message("Ya existe un VIN registrado.")
                            .value(c.getVin())
                            .build());
                }
            }

            if (notBlank(c.getChassisNumber()) && ctx.chassisPaths.containsKey(c.getChassisNumber())) {
                for (String p : ctx.chassisPaths.get(c.getChassisNumber())) {
                    errors.add(PurchaseFieldError.builder()
                            .path(p)
                            .message("Ya existe un chassisNumber registrado.")
                            .value(c.getChassisNumber())
                            .build());
                }
            }
        }

        // Selecciona un code “principal” para el error global
        String code = "DUPLICATE_IN_DB";
        String msg = "Ya existen identificadores seriales registrados en el sistema.";
        if (errors.stream().anyMatch(e -> e.getMessage().contains("engineNumber"))) {
            code = "DUPLICATE_ENGINE_NUMBER";
        } else if (errors.stream().anyMatch(e -> e.getMessage().contains("VIN"))) {
            code = "DUPLICATE_VIN";
        } else if (errors.stream().anyMatch(e -> e.getMessage().contains("chassisNumber"))) {
            code = "DUPLICATE_CHASSIS_NUMBER";
        }

        return new PurchaseApiException(422, code, msg, errors);
    }

    // ---------------------------------------------------------------------
    // HELPERS
    // ---------------------------------------------------------------------

    private static String path(int itemIndex, String field) {
        return "items[" + itemIndex + "]." + field;
    }

    private static boolean isWholeNumber(BigDecimal value) {
        if (value == null) return false;
        return value.stripTrailingZeros().scale() <= 0;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static String normalizeId(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        return t.toUpperCase(Locale.ROOT);
    }

    private static String normalizeText(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String normalizeCategory(String s) {
        if (s == null) return null;
        String t = s.trim().toUpperCase(Locale.ROOT);
        return t.isEmpty() ? null : t;
    }

    private static boolean isMotorOrMoto(String category) {
        return "MOTOR".equals(category) || "MOTOCICLETAS".equals(category);
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

    private static void validateIdField(String value,
                                        int min,
                                        int max,
                                        int itemIndex,
                                        int serialIndex,
                                        String field) {
        if (!notBlank(value)) return;
        if (value.length() < min || value.length() > max) {
            throw new PurchaseApiException(422, "INVALID_FIELD_LENGTH", field + " inválido.",
                    List.of(PurchaseFieldError.builder()
                            .path(path(itemIndex, "serialUnits[" + serialIndex + "]." + field))
                            .message(field + " debe tener longitud entre " + min + " y " + max)
                            .value(value)
                            .expected(min + ".." + max)
                            .build()));
        }
        if (!ID_PATTERN.matcher(value).matches()) {
            throw new PurchaseApiException(422, "INVALID_FIELD_FORMAT", field + " inválido.",
                    List.of(PurchaseFieldError.builder()
                            .path(path(itemIndex, "serialUnits[" + serialIndex + "]." + field))
                            .message(field + " solo puede contener A-Z, 0-9 y guión (-)")
                            .value(value)
                            .expected("A-Z0-9-")
                            .build()));
        }
    }

    private static class ValidationContext {
        private final Map<Long, ProductFlags> flagsByProductId = new HashMap<>();

        private final Set<String> allVins = new HashSet<>();
        private final Set<String> allEngineNumbers = new HashSet<>();
        private final Set<String> allChassisNumbers = new HashSet<>();

        private final Map<String, List<String>> vinPaths = new HashMap<>();
        private final Map<String, List<String>> enginePaths = new HashMap<>();
        private final Map<String, List<String>> chassisPaths = new HashMap<>();
    }
}
