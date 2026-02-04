package com.paulfernandosr.possystembackend.catalog.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.catalog.application.*;
import com.paulfernandosr.possystembackend.catalog.domain.*;
import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;

@RestController
@RequestMapping("/catalog")
@RequiredArgsConstructor
public class CatalogRestController {

    private final CompatibilityService compatibilityService;
    private final ProductTypeService productTypeService;
    private final PresentationService presentationService;
    private final OriginCountryService originCountryService;
    private final BrandService brandService;
    private final ModelService modelService;

    // --------------- COMPATIBILIDADES ---------------

    @PostMapping("/compatibilities")
    public ResponseEntity<Void> createCompatibility(@RequestBody Compatibility compatibility) {
        compatibilityService.create(compatibility);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @GetMapping("/compatibilities")
    public ResponseEntity<SuccessResponse<Collection<Compatibility>>> getCompatibilities() {
        return ResponseEntity.ok(
                SuccessResponse.ok(compatibilityService.findAll())
        );
    }

    // --------------- TIPO DE PRODUCTO ---------------

    @PostMapping("/product-types")
    public ResponseEntity<Void> createProductType(@RequestBody ProductType productType) {
        productTypeService.create(productType);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @GetMapping("/product-types")
    public ResponseEntity<SuccessResponse<Collection<ProductType>>> getProductTypes() {
        return ResponseEntity.ok(
                SuccessResponse.ok(productTypeService.findAll())
        );
    }

    // --------------- PRESENTACIONES ---------------

    @PostMapping("/presentations")
    public ResponseEntity<Void> createPresentation(@RequestBody Presentation presentation) {
        presentationService.create(presentation);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @GetMapping("/presentations")
    public ResponseEntity<SuccessResponse<Collection<Presentation>>> getPresentations() {
        return ResponseEntity.ok(
                SuccessResponse.ok(presentationService.findAll())
        );
    }

    // --------------- PAÍSES DE ORIGEN ---------------

    @PostMapping("/origin-countries")
    public ResponseEntity<Void> createOriginCountry(@RequestBody OriginCountry originCountry) {
        originCountryService.create(originCountry);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @GetMapping("/origin-countries")
    public ResponseEntity<SuccessResponse<Collection<OriginCountry>>> getOriginCountries() {
        return ResponseEntity.ok(
                SuccessResponse.ok(originCountryService.findAll())
        );
    }

    // --------------- MARCAS ---------------

    @PostMapping("/brands")
    public ResponseEntity<Void> createBrand(@RequestBody Brand brand) {
        brandService.create(brand);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @GetMapping("/brands")
    public ResponseEntity<SuccessResponse<Collection<Brand>>> getBrands() {
        return ResponseEntity.ok(
                SuccessResponse.ok(brandService.findAll())
        );
    }

    // --------------- MODELOS (independiente) ---------------

    @PostMapping("/models")
    public ResponseEntity<Void> createModel(@RequestBody Model model) {
        modelService.create(model);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @GetMapping("/models")
    public ResponseEntity<SuccessResponse<Collection<Model>>> getModels() {
        return ResponseEntity.ok(
                SuccessResponse.ok(modelService.findAll())
        );
    }
}
