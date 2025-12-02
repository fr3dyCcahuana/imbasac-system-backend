package com.paulfernandosr.possystembackend.category.application;

import com.paulfernandosr.possystembackend.category.domain.Category;
import com.paulfernandosr.possystembackend.category.domain.exception.CategoryAlreadyExistsException;
import com.paulfernandosr.possystembackend.category.domain.port.input.CreateNewCategoryUseCase;
import com.paulfernandosr.possystembackend.category.domain.port.output.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CreateNewCategoryService implements CreateNewCategoryUseCase {
    private final CategoryRepository categoryRepository;

    @Override
    public void createNewCategory(Category category) {
        boolean doesCategoryExists = categoryRepository.existsByName(category.getName());

        if (doesCategoryExists) {
            throw new CategoryAlreadyExistsException("Category already exists with name: " + category.getName());
        }

        categoryRepository.create(category);
    }
}
