package com.paulfernandosr.possystembackend.supplier.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.common.domain.Page;
import com.paulfernandosr.possystembackend.common.domain.Pageable;
import com.paulfernandosr.possystembackend.common.infrastructure.mapper.PageMapper;
import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import com.paulfernandosr.possystembackend.supplier.domain.Supplier;
import com.paulfernandosr.possystembackend.supplier.domain.port.input.CreateNewSupplierUseCase;
import com.paulfernandosr.possystembackend.supplier.domain.port.input.GetPageOfSuppliersUseCase;
import com.paulfernandosr.possystembackend.supplier.domain.port.input.GetSupplierInfoUseCase;
import com.paulfernandosr.possystembackend.supplier.domain.port.input.ResolveSupplierUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;

@RestController
@RequiredArgsConstructor
@RequestMapping("/suppliers")
public class SupplierRestController {

    private final CreateNewSupplierUseCase createNewSupplierUseCase;
    private final ResolveSupplierUseCase resolveSupplierUseCase;
    private final GetSupplierInfoUseCase getSupplierInfoUseCase;
    private final GetPageOfSuppliersUseCase getPageOfSuppliersUseCase;

    @PostMapping
    public ResponseEntity<Void> createNewSupplier(@RequestBody Supplier supplier) {
        createNewSupplierUseCase.createNewSupplier(supplier);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @PostMapping("/resolve")
    public ResponseEntity<SuccessResponse<Supplier>> resolveSupplier(@RequestBody Supplier supplier) {
        return ResponseEntity.ok(SuccessResponse.ok(resolveSupplierUseCase.resolveSupplier(supplier)));
    }

    @GetMapping("/{supplierId}")
    public ResponseEntity<SuccessResponse<Supplier>> getSupplierInfoById(@PathVariable Long supplierId) {
        return ResponseEntity.ok(SuccessResponse.ok(getSupplierInfoUseCase.getSupplierInfoById(supplierId)));
    }

    @GetMapping
    public ResponseEntity<SuccessResponse<Collection<Supplier>>> getPageOfSuppliers(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<Supplier> pageOfSuppliers = getPageOfSuppliersUseCase.getPageOfSuppliers(query, new Pageable(page, size));
        SuccessResponse.Metadata metadata = PageMapper.mapPage(pageOfSuppliers);
        return ResponseEntity.ok(SuccessResponse.ok(pageOfSuppliers.getContent(), metadata));
    }
}
