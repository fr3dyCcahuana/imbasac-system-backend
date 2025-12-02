package com.paulfernandosr.possystembackend.category.domain.port.input;

import com.paulfernandosr.possystembackend.category.domain.Category;

public interface CreateNewCategoryUseCase {
    void createNewCategory(Category category);
}
