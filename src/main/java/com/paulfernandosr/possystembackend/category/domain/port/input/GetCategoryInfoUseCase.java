package com.paulfernandosr.possystembackend.category.domain.port.input;

import com.paulfernandosr.possystembackend.category.domain.Category;

public interface GetCategoryInfoUseCase {
    Category getCategoryInfoById(Long categoryId);
}
