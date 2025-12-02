package com.paulfernandosr.possystembackend.category.application;

import com.paulfernandosr.possystembackend.category.domain.Category;
import com.paulfernandosr.possystembackend.category.domain.port.input.GetAllCategoriesUseCase;
import com.paulfernandosr.possystembackend.category.domain.port.output.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class GetAllCategoriesService implements GetAllCategoriesUseCase {
    private final CategoryRepository categoryRepository;

    @Override
    public Collection<Category> getAllCategories() {
        return categoryRepository.findAll();
    }
}
