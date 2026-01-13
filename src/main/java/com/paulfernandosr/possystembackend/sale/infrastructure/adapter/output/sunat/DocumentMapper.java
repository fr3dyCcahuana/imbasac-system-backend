package com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.sunat;

import com.paulfernandosr.possystembackend.customer.domain.Customer;
import com.paulfernandosr.possystembackend.sale.domain.Sale;
import com.paulfernandosr.possystembackend.sale.domain.SaleItem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.Collection;

public class DocumentMapper {
    public static final String EMPTY_STRING = "";
    public static final String ONE_SPACE = " ";
    public static final BigDecimal IGV_FACTOR = new BigDecimal("1.18");
    public static final BigDecimal IGV_RATE = new BigDecimal("0.18");
    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static DocumentRequest.Customer mapCustomer(Customer customer) {
        return DocumentRequest.Customer.builder()
                .fullName(customer.getLegalName())
                .documentNumber(customer.getDocumentNumber())
                .entityTypeCode(IdentityDocumentType.valueOf(customer.getDocumentType().toString()).getCode())
                .address(customer.getAddress())
                .build();
    }

    public static DocumentRequest.Sale mapSale(Sale sale) {
        BigDecimal totalBasePrice = calculateTotalBasePrice(sale.getItems());
        BigDecimal totalIgv = calculateTotalIgv(totalBasePrice);
        BigDecimal globalDiscount = calculateGlobalDiscount(sale.getDiscount());

        return DocumentRequest.Sale.builder()
                .serial(sale.getSerial())
                .number(sale.getNumber().toString())
                .issueDate(sale.getIssuedAt().toLocalDate().format(DATE_FORMATTER))
                .issueTime(sale.getIssuedAt().toLocalTime().format(TIME_FORMATTER))
                .dueDate(EMPTY_STRING)
                .currencyId(CurrencyType.SOL.getCode())
                .paymentMethodId(PaymentMethod.CASH.getCode())
                .totalTaxed(totalBasePrice.toString())
                .totalIgv(totalIgv.toString())
                .totalExempted(EMPTY_STRING)
                .totalUnaffected(EMPTY_STRING)
                .globalDiscount(globalDiscount.toString())
                .documentTypeCode(DocumentType.fromSaleType(sale.getType()).getCode())
                .note(sale.getComment())
                .build();
    }

    private static BigDecimal calculateTotalBasePrice(Collection<SaleItem> saleItems) {
        return saleItems.stream()
                .map(saleItem -> calculateBasePrice(saleItem.getPrice())
                        .multiply(new BigDecimal(saleItem.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal calculateTotalIgv(BigDecimal totalBasePrice) {
        return totalBasePrice
                .multiply(IGV_RATE)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal calculateGlobalDiscount(BigDecimal totalDiscount) {
        return calculateBasePrice(totalDiscount)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public static Collection<DocumentRequest.Item> mapSaleItems(Collection<SaleItem> saleItems) {
        return saleItems
                .stream()
                .map(DocumentMapper::mapSaleItem)
                .toList();
    }

    private static DocumentRequest.Item mapSaleItem(SaleItem saleItem) {
        return DocumentRequest.Item.builder()
                .product(saleItem.getProduct().getName())
                .quantity(String.valueOf(saleItem.getQuantity()))
                .basePrice(calculateBasePrice(saleItem.getPrice()).toString())
                .sunatCode("-")
                .productCode("getProductCode(saleItem)")
                .unitCode(UnitOfMeasureType.PRODUCT_UNIT.getCode())
                .igvTypeCode(IgvType.TAXABLE_ONEROUS.getCode())
                .build();
    }

/*    private static String getProductCode(SaleItem saleItem) {
        if (saleItem.getProduct().getOriginCode() != null) {
            return saleItem.getProduct().getOriginCode();
        }
        return saleItem.getProduct().getBarcode();
    }*/

    private static BigDecimal calculateBasePrice(BigDecimal price) {
        return price.divide(IGV_FACTOR, 6, RoundingMode.HALF_UP);
    }
}

