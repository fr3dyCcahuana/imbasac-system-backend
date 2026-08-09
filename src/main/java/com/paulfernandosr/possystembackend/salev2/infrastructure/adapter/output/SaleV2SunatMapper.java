package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.common.infrastructure.sunat.SunatProductCodeValidator;
import com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.sunat.CurrencyType;
import com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.sunat.DocumentRequest;
import com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.sunat.IgvType;
import com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.sunat.PaymentMethod;
import com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.sunat.SunatProps;
import com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.sunat.UnitOfMeasureType;
import com.paulfernandosr.possystembackend.salev2.domain.exception.InvalidSaleV2Exception;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.SaleV2SunatRepository;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output.sunat.SunatCodeInferer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public final class SaleV2SunatMapper {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final String MOTORCYCLE_SUNAT_CODE = "25101801";

    private SaleV2SunatMapper() {
    }

    public static DocumentRequest map(SunatProps props,
                                      SaleV2SunatRepository.LockedSunatSale sale,
                                      List<SaleV2SunatRepository.SaleItemForSunat> items) {

        SunatProps.Business business = props.getBusiness();
        LocalDateTime baseDateTime = sale.getCreatedAt() != null ? sale.getCreatedAt() : LocalDateTime.now();
        LocalDateTime emissionDateTime = sale.getIssueDate() != null
                ? sale.getIssueDate().atTime(baseDateTime.toLocalTime())
                : baseDateTime;

        validateMotorcycleItems(items);

        return DocumentRequest.builder()
                .business(DocumentRequest.Business.builder()
                        .ruc(business.getRuc())
                        .businessName(business.getBusinessName())
                        .tradeName(business.getTradeName())
                        .taxAddress(business.getTaxAddress())
                        .ubigeo(business.getUbigeo())
                        .neighborhood(business.getNeighborhood())
                        .district(business.getDistrict())
                        .province(business.getProvince())
                        .department(business.getDepartment())
                        .mode(props.getMode())
                        .username(props.getUsername())
                        .password(props.getPassword())
                        .build())
                .customer(DocumentRequest.Customer.builder()
                        .fullName(required(sale.getCustomerName(), "customerName"))
                        .documentNumber(mapCustomerDocumentNumber(sale.getCustomerDocType(), sale.getCustomerDocNumber()))
                        .entityTypeCode(mapIdentityDocumentCode(sale.getDocType(), sale.getCustomerDocType()))
                        .address(blankIfNull(sale.getCustomerAddress()))
                        .build())
                .sale(DocumentRequest.Sale.builder()
                        .serial(required(sale.getSeries(), "series"))
                        .number(String.valueOf(sale.getNumber()))
                        .issueDate(emissionDateTime.toLocalDate().format(DATE_FORMATTER))
                        .issueTime(emissionDateTime.toLocalTime().format(TIME_FORMATTER))
                        .dueDate("")
                        .currencyId(mapCurrencyCode(sale.getCurrency()))
                        .paymentMethodId(PaymentMethod.CASH.getCode())
                        .totalTaxed(totalTaxed(sale).toPlainString())
                        .totalIgv(totalIgv(sale).toPlainString())
                        .totalExempted(totalExempted(sale).toPlainString())
                        .totalUnaffected(totalUnaffected(sale).toPlainString())
                        .globalDiscount(nz(sale.getDiscountTotal()).setScale(2, RoundingMode.HALF_UP).toPlainString())
                        .documentTypeCode(mapDocumentCode(sale.getDocType()))
                        .note(blankIfNull(sale.getNotes()))
                        .build())
                .items(items.stream().map(i -> mapItem(i, sale)).toList())
                .build();
    }

    private static DocumentRequest.Item mapItem(SaleV2SunatRepository.SaleItemForSunat item,
                                                SaleV2SunatRepository.LockedSunatSale sale) {
        BigDecimal qty = nz(item.getQuantity());
        if (qty.signum() <= 0) {
            throw new InvalidSaleV2Exception("Cantidad inválida para emisión SUNAT en línea " + item.getLineNumber());
        }

        BigDecimal basePrice = nz(item.getRevenueTotal())
                .divide(qty, 6, RoundingMode.HALF_UP);

        String productCategory = required(item.getProductCategory(), "productCategory");
        boolean motorcycle = isMotorcycle(item);
        boolean motor = isMotor(item);
        String productDescription = motorcycle
                ? buildMotorcycleSunatDescription(item)
                : motor
                    ? buildMotorSunatDescription(item)
                    : required(item.getDescription(), "description");

        String sunatCode = resolveSunatProductCode(item, productDescription, productCategory, motorcycle);

        return DocumentRequest.Item.builder()
                .product(productDescription)
                .quantity(qty.stripTrailingZeros().toPlainString())
                .basePrice(basePrice.toPlainString())
                .sunatCode(sunatCode)
                .productCode(blankIfNull(item.getSku()))
                .unitCode(UnitOfMeasureType.PRODUCT_UNIT.getCode())
                .igvTypeCode(resolveIgvTypeCode(sale))
                .build();
    }

    private static String resolveSunatProductCode(SaleV2SunatRepository.SaleItemForSunat item,
                                                  String productDescription,
                                                  String productCategory,
                                                  boolean motorcycle) {
        String context = "Codigo Producto SUNAT linea " + item.getLineNumber();
        String configured = blankIfNull(item.getSunatProductCode()).trim();

        try {
            if (!configured.isBlank()) {
                return SunatProductCodeValidator.requireValid(configured, context);
            }

            if (motorcycle) {
                return SunatProductCodeValidator.requireValid(MOTORCYCLE_SUNAT_CODE, context);
            }

            return SunatCodeInferer.infer(productDescription, productCategory);
        } catch (Exception ex) {
            throw new InvalidSaleV2Exception(
                    "No se pudo resolver Codigo Producto SUNAT valido para la linea " + item.getLineNumber()
                            + " producto=" + productDescription
                            + " categoria=" + productCategory
                            + ". Detalle: " + ex.getMessage()
            );
        }
    }

    private static String buildMotorcycleCommercialDescription(SaleV2SunatRepository.SaleItemForSunat item) {
        String description = cleanCsv(required(item.getDescription(), "description"));
        String brand = cleanCsv(item.getBrand());
        String model = cleanCsv(item.getModel());

        if (brand.isBlank()) {
            throw new InvalidSaleV2Exception(
                    "Falta dato de motocicleta para SUNAT: Marca. Línea=" + item.getLineNumber()
            );
        }

        if (model.isBlank()) {
            throw new InvalidSaleV2Exception(
                    "Falta dato de motocicleta para SUNAT: Modelo. Línea=" + item.getLineNumber()
            );
        }

        String result = description;

        if (!containsToken(result, brand)) {
            result = result + " " + brand;
        }

        if (!containsToken(result, model)) {
            result = result + " " + model;
        }

        return cleanCsv(result);
    }

    private static boolean containsToken(String text, String token) {
        if (text == null || token == null || token.isBlank()) {
            return false;
        }

        return text.toUpperCase(Locale.ROOT).contains(token.toUpperCase(Locale.ROOT));
    }

    /**
     * Formato requerido por el XML de referencia de motocicletas:
     * DESCRIPCION,L3,,MOTOCICLETA,AÑO_MODELO,AÑO_MODELO,AÑO_FABRICACION,MOTOR,CHASIS,VIN,COLOR,COLOR,
     * CAPACIDAD,CILINDROS,ASIENTOS,MECANICO,EJES,FORMA_RODANTE,,RUEDAS,,COMBUSTIBLE,PASAJEROS,
     * PESO_BRUTO,PESO_NETO,CARGA_UTIL,ALTO,LARGO,ANCHO
     */
    private static String buildMotorcycleSunatDescription(SaleV2SunatRepository.SaleItemForSunat item) {
        String yearModel = cleanCsv(item.getYearMake());

        return String.join(",",
                buildMotorcycleCommercialDescription(item),      // 1: descripción + marca + modelo
                cleanCsv(item.getVehicleClass()),                          // 2
                "",                                                        // 3
                cleanCsv(defaultIfBlank(item.getBodywork(), "MOTOCICLETA")),// 4
                yearModel,                                                 // 5: tu BD ya no guarda year_model; se usa year_make como fallback
                yearModel,                                                 // 6: tu BD ya no guarda year_model; se usa year_make como fallback
                cleanCsv(item.getYearMake()),                              // 7
                cleanCsv(item.getEngineNumber()),                          // 8
                cleanCsv(item.getChassisNumber()),                         // 9
                cleanCsv(item.getVin()),                                   // 10
                cleanCsv(item.getColor()),                                 // 11
                cleanCsv(item.getColor()),                                 // 12
                cleanCsv(formatEngineCapacity(item.getEngineCapacity())),   // 13
                cleanCsv(item.getCylinders()),                             // 14
                cleanCsv(item.getSeats()),                                 // 15
                "MECANICO",                                                // 16
                cleanCsv(item.getAxles()),                                 // 17
                cleanCsv(item.getRollingForm()),                           // 18
                "",                                                        // 19
                cleanCsv(item.getWheels()),                                // 20
                "",                                                        // 21
                cleanCsv(item.getFuel()),                                  // 22
                cleanCsv(item.getPassengers()),                            // 23
                cleanCsv(item.getGrossWeight()),                           // 24
                cleanCsv(item.getNetWeight()),                             // 25
                cleanCsv(item.getPayload()),                               // 26
                cleanCsv(item.getHeight()),                                // 27
                cleanCsv(item.getLength()),                                // 28
                cleanCsv(item.getWidth())                                  // 29
        );
    }

    private static String buildMotorSunatDescription(SaleV2SunatRepository.SaleItemForSunat item) {
        return String.join(", ",
                nonBlankSegments(
                        buildMotorCommercialDescription(item),
                        labelled("MARCA", item.getBrand()),
                        labelled("COLOR", item.getColor()),
                        labelled("MODELO", item.getModel()),
                        labelled("NUM. MOTOR", item.getEngineNumber()),
                        labelled("DUA", item.getDuaNumber()),
                        labelled("ITEM DUA", item.getDuaItem()),
                        labelled("ANIO FABRICACION", item.getYearMake()),
                        labelled("CARROCERIA", defaultIfBlank(item.getBodywork(), "MOTOR")),
                        labelled("CAPAC. MOTOR", formatEngineCapacity(item.getEngineCapacity())),
                        labelled("COMBUSTIBLE", item.getFuel()),
                        labelled("NUM. CILINDROS", item.getCylinders()),
                        labelled("PESO NETO", item.getNetWeight()),
                        labelled("CARGA UTIL", item.getPayload()),
                        labelled("PESO BRUTO", item.getGrossWeight())
                )
        );
    }

    private static String buildMotorCommercialDescription(SaleV2SunatRepository.SaleItemForSunat item) {
        String description = cleanCsv(required(item.getDescription(), "description"));
        String brand = cleanCsv(item.getBrand());
        String model = cleanCsv(item.getModel());
        String result = description;

        if (!brand.isBlank() && !containsToken(result, brand)) {
            result = result + " " + brand;
        }

        if (!model.isBlank() && !containsToken(result, model)) {
            result = result + " " + model;
        }

        return cleanCsv(result);
    }

    private static void validateMotorcycleItems(List<SaleV2SunatRepository.SaleItemForSunat> items) {
        if (items == null) return;

        for (SaleV2SunatRepository.SaleItemForSunat item : items) {
            if (isMotor(item)) {
                validateMotorItem(item);
                continue;
            }

            if (!isMotorcycle(item)) continue;

            if (nz(item.getQuantity()).compareTo(BigDecimal.ONE) != 0) {
                throw new InvalidSaleV2Exception(
                        "Cada motocicleta debe emitirse con cantidad 1. Línea=" + item.getLineNumber()
                );
            }

            requireMotorcycle(item.getDescription(), "Descripción", item);
            requireMotorcycle(item.getVehicleClass(), "Clase vehicular", item);
            requireMotorcycle(item.getBodywork(), "Carrocería", item);
            requireMotorcycle(item.getYearMake(), "Año fabricación", item);
            requireMotorcycle(item.getEngineNumber(), "Número de motor", item);
            requireMotorcycle(item.getChassisNumber(), "Número de chasis", item);
            requireMotorcycle(item.getVin(), "VIN", item);
            requireMotorcycle(item.getColor(), "Color", item);
            requireMotorcycle(item.getEngineCapacity(), "Capacidad motor", item);
            requireMotorcycle(item.getCylinders(), "Número de cilindros", item);
            requireMotorcycle(item.getSeats(), "Número de asientos", item);
            requireMotorcycle(item.getAxles(), "Número de ejes", item);
            requireMotorcycle(item.getRollingForm(), "Forma rodante", item);
            requireMotorcycle(item.getWheels(), "Número de ruedas", item);
            requireMotorcycle(item.getFuel(), "Combustible", item);
            requireMotorcycle(item.getPassengers(), "Número de pasajeros", item);
            requireMotorcycle(item.getGrossWeight(), "Peso bruto", item);
            requireMotorcycle(item.getNetWeight(), "Peso neto", item);
            requireMotorcycle(item.getHeight(), "Alto", item);
            requireMotorcycle(item.getLength(), "Largo", item);
            requireMotorcycle(item.getWidth(), "Ancho", item);
        }
    }

    private static void requireMotorcycle(Object value, String label, SaleV2SunatRepository.SaleItemForSunat item) {
        if (value == null || String.valueOf(value).trim().isBlank()) {
            throw new InvalidSaleV2Exception(
                    "Falta dato de motocicleta para SUNAT: " + label + ". Línea=" + item.getLineNumber()
            );
        }
    }

    private static void validateMotorItem(SaleV2SunatRepository.SaleItemForSunat item) {
        if (nz(item.getQuantity()).compareTo(BigDecimal.ONE) != 0) {
            throw new InvalidSaleV2Exception(
                    "Cada motor debe emitirse con cantidad 1. Linea=" + item.getLineNumber()
            );
        }

        requireMotor(item.getDescription(), "Descripcion", item);
        requireMotor(item.getEngineNumber(), "Numero de motor", item);
        requireMotor(item.getYearMake(), "Anio fabricacion", item);
        requireMotor(item.getBodywork(), "Carroceria", item);
        requireMotor(item.getColor(), "Color", item);
        requireMotor(item.getEngineCapacity(), "Capacidad motor", item);
        requireMotor(item.getFuel(), "Combustible", item);
        requireMotor(item.getCylinders(), "Numero de cilindros", item);
        requireMotor(item.getGrossWeight(), "Peso bruto", item);
        requireMotor(item.getNetWeight(), "Peso neto", item);
    }

    private static void requireMotor(Object value, String label, SaleV2SunatRepository.SaleItemForSunat item) {
        if (value == null || String.valueOf(value).trim().isBlank()) {
            throw new InvalidSaleV2Exception(
                    "Falta dato de motor para SUNAT: " + label + ". Linea=" + item.getLineNumber()
            );
        }
    }

    private static boolean isMotorcycle(SaleV2SunatRepository.SaleItemForSunat item) {
        String category = normalize(item.getProductCategory());
        String type = normalize(item.getVehicleType());
        return "MOTOCICLETAS".equals(category)
                || "MOTOCICLETA".equals(category)
                || "MOTOCICLETA".equals(type);
    }

    private static boolean isMotor(SaleV2SunatRepository.SaleItemForSunat item) {
        String category = normalize(item.getProductCategory());
        String type = normalize(item.getVehicleType());
        return "MOTOR".equals(category) || "MOTOR".equals(type);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String formatEngineCapacity(String value) {
        String v = cleanCsv(value);
        if (v.isBlank()) return "";
        String upper = v.toUpperCase(Locale.ROOT);
        return upper.contains("CC") ? v : v + " CC";
    }

    private static String cleanCsv(Object value) {
        if (value == null) return "";

        String text;
        if (value instanceof BigDecimal bd) {
            text = bd.stripTrailingZeros().toPlainString();
        } else {
            text = String.valueOf(value);
        }

        return text
                .trim()
                .replace(",", " ")
                .replaceAll("\\s+", " ");
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.trim().isBlank() ? fallback : value.trim();
    }

    private static String labelled(String label, Object value) {
        String text = cleanCsv(value);
        return text.isBlank() ? "" : label + ": " + text;
    }

    private static List<String> nonBlankSegments(String... values) {
        return java.util.Arrays.stream(values)
                .filter(v -> v != null && !v.trim().isBlank())
                .toList();
    }

    private static String mapDocumentCode(String docType) {
        String v = required(docType, "docType").trim().toUpperCase();
        return switch (v) {
            case "FACTURA" -> "01";
            case "BOLETA" -> "03";
            default -> throw new InvalidSaleV2Exception("Solo se puede emitir a SUNAT documentos BOLETA/FACTURA. docType=" + docType);
        };
    }

    private static String mapIdentityDocumentCode(String saleDocType, String customerDocType) {
        String saleDoc = required(saleDocType, "docType").trim().toUpperCase();
        String customerDoc = required(customerDocType, "customerDocType").trim().toUpperCase();

        if (isGenericCustomerDocumentType(customerDoc)) {
            if ("FACTURA".equals(saleDoc)) {
                throw new InvalidSaleV2Exception("FACTURA no permite customerDocType=GEN/0. Debe usar RUC.");
            }
            return "0";
        }

        return switch (customerDoc) {
            case "DNI" -> "1";
            case "CE", "CARNET DE EXTRANJERIA", "CARNET DE EXTRANJERÍA" -> "4";
            case "RUC" -> "6";
            case "PASSPORT", "PASAPORTE" -> "7";
            default -> throw new InvalidSaleV2Exception("Tipo de documento de cliente no soportado para SUNAT: " + customerDocType);
        };
    }

    private static String mapCustomerDocumentNumber(String customerDocType, String customerDocNumber) {
        if (isGenericCustomerDocumentType(customerDocType)) {
            return "0";
        }
        return required(customerDocNumber, "customerDocNumber").trim();
    }

    private static boolean isGenericCustomerDocumentType(String value) {
        String v = blankIfNull(value).trim().toUpperCase();
        return switch (v) {
            case "GEN", "GENERICO", "GENÉRICO", "GENERAL", "0",
                 "OTROS", "SIN_DOCUMENTO", "SIN DOCUMENTO" -> true;
            default -> false;
        };
    }

    private static String mapCurrencyCode(String currency) {
        String v = required(currency, "currency").trim().toUpperCase();
        return switch (v) {
            case "PEN" -> CurrencyType.SOL.getCode();
            case "USD" -> CurrencyType.DOLLAR.getCode();
            default -> throw new InvalidSaleV2Exception("Moneda no soportada para SUNAT: " + currency);
        };
    }

    private static BigDecimal totalTaxed(SaleV2SunatRepository.LockedSunatSale sale) {
        return isNoGravada(sale) ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : nz(sale.getSubtotal()).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal totalIgv(SaleV2SunatRepository.LockedSunatSale sale) {
        return isNoGravada(sale) ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : nz(sale.getIgvAmount()).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal totalExempted(SaleV2SunatRepository.LockedSunatSale sale) {
        return isNoGravada(sale)
                ? nz(sale.getSubtotal()).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal totalUnaffected(SaleV2SunatRepository.LockedSunatSale sale) {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private static String resolveIgvTypeCode(SaleV2SunatRepository.LockedSunatSale sale) {
        return isNoGravada(sale) ? "20" : IgvType.TAXABLE_ONEROUS.getCode();
    }

    private static boolean isNoGravada(SaleV2SunatRepository.LockedSunatSale sale) {
        return "NO_GRAVADA".equalsIgnoreCase(blankIfNull(sale.getTaxStatus()));
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String blankIfNull(String value) {
        return value == null ? "" : value;
    }

    private static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new InvalidSaleV2Exception("Campo obligatorio para emisión SUNAT: " + field);
        }
        return value;
    }
}
