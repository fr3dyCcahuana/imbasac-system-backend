package com.paulfernandosr.possystembackend.category.domain.port.input;

import com.paulfernandosr.possystembackend.category.domain.Category;

import java.util.Collection;

public interface GetAllCategoriesUseCase {
    Collection<Category> getAllCategories();
}
