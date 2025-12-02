package com.paulfernandosr.possystembackend.category.application;

import com.paulfernandosr.possystembackend.category.domain.Category;
import com.paulfernandosr.possystembackend.category.domain.exception.CategoryNotFoundException;
import com.paulfernandosr.possystembackend.category.domain.port.input.GetCategoryInfoUseCase;
import com.paulfernandosr.possystembackend.category.domain.port.output.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetCategoryInfoService implements GetCategoryInfoUseCase {
    private final CategoryRepository categoryRepository;

    @Override
    public Category getCategoryInfoById(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException("Category not found with identification: " + categoryId));
    }
}
