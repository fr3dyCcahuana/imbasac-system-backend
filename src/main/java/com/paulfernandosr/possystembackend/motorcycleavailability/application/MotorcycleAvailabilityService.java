package com.paulfernandosr.possystembackend.motorcycleavailability.application;

import com.paulfernandosr.possystembackend.motorcycleavailability.domain.*;
import com.paulfernandosr.possystembackend.product.domain.Product;
import com.paulfernandosr.possystembackend.product.domain.ProductImage;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductImageRepository;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductRepository;
import com.paulfernandosr.possystembackend.product.infrastructure.adapter.input.ProductImagePublicUrlService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class MotorcycleAvailabilityService {

    private final MotorcycleAvailabilityRepository motorcycleAvailabilityRepository;
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductImagePublicUrlService productImagePublicUrlService;

    public MotorcycleAvailabilityPage findPage(MotorcycleAvailabilityQuery query) {
        MotorcycleAvailabilityPage page = motorcycleAvailabilityRepository.findPage(query);
        page.getResponse().getItems().forEach(item ->
                item.setMainImageUrl(productImagePublicUrlService.toPublicUrl(item.getMainImageUrl()))
        );
        return page;
    }

    public MotorcycleAvailabilityFilterOptions findFilterOptions() {
        return motorcycleAvailabilityRepository.findFilterOptions();
    }

    public MotorcycleAvailabilityUnitsResponse findUnits(Long productId) {
        if (productId == null || productId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "productId es obligatorio.");
        }

        return MotorcycleAvailabilityUnitsResponse.builder()
                .productId(productId)
                .units(motorcycleAvailabilityRepository.findUnits(productId))
                .build();
    }

    public MotorcycleContractPrefillResponse contractPrefill(Long productId, Long serialUnitId, String priceList) {
        if (productId == null || productId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "productId es obligatorio.");
        }
        if (serialUnitId == null || serialUnitId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "serialUnitId es obligatorio.");
        }

        String list = normalizePriceList(priceList);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado."));

        if (!"MOTOCICLETAS".equalsIgnoreCase(blank(product.getCategory()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El producto no es una motocicleta.");
        }

        if (!Boolean.TRUE.equals(product.getManageBySerial())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La motocicleta no maneja VIN/serie.");
        }

        MotorcycleAvailabilityUnit unit = motorcycleAvailabilityRepository.findUnit(productId, serialUnitId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unidad serial no encontrada."));

        if (!unit.isCanCreateContract()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La unidad ya no está disponible para contrato.");
        }

        Collection<ProductImage> images = new ArrayList<>(productImageRepository.findByProductId(productId));
        productImagePublicUrlService.enrich(images);
        product.setImages(new ArrayList<>(images));

        return MotorcycleContractPrefillResponse.builder()
                .product(product)
                .serialUnit(unit)
                .priceList(list)
                .cashPrice(resolvePrice(product, list))
                .canCreateContract(true)
                .build();
    }

    private static String normalizePriceList(String value) {
        String normalized = blank(value).toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return "A";
        }
        if (!normalized.matches("[ABCD]")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "priceList debe ser A, B, C o D.");
        }
        return normalized;
    }

    private static BigDecimal resolvePrice(Product product, String priceList) {
        BigDecimal price = switch (priceList) {
            case "B" -> product.getPriceB();
            case "C" -> product.getPriceC();
            case "D" -> product.getPriceD();
            default -> product.getPriceA();
        };
        return price == null ? BigDecimal.ZERO : price;
    }

    private static String blank(String value) {
        return value == null ? "" : value.trim();
    }
}
