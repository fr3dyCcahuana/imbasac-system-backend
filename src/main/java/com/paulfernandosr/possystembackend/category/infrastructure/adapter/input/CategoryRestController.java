package com.paulfernandosr.possystembackend.category.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.category.domain.Category;
import com.paulfernandosr.possystembackend.category.domain.port.input.CreateNewCategoryUseCase;
import com.paulfernandosr.possystembackend.category.domain.port.input.GetAllCategoriesUseCase;
import com.paulfernandosr.possystembackend.category.domain.port.input.GetCategoryInfoUseCase;
import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;

@RestController
@RequiredArgsConstructor
@RequestMapping("/categories")
public class CategoryRestController {
    private final CreateNewCategoryUseCase createNewCategoryUseCase;
    private final GetAllCategoriesUseCase getAllCategoriesUseCase;
    private final GetCategoryInfoUseCase getCategoryInfoUseCase;

    @PostMapping
    public ResponseEntity<Void> createNewCategory(@RequestBody Category category) {
        createNewCategoryUseCase.createNewCategory(category);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<SuccessResponse<Collection<Category>>> getAllCategories() {
        return ResponseEntity.ok(SuccessResponse.ok(getAllCategoriesUseCase.getAllCategories()));
    }

    @GetMapping("/{categoryId}")
    public ResponseEntity<SuccessResponse<Category>> getCategoryInfo(@PathVariable Long categoryId) {
        return ResponseEntity.ok(SuccessResponse.ok(getCategoryInfoUseCase.getCategoryInfoById(categoryId)));
    }

}
