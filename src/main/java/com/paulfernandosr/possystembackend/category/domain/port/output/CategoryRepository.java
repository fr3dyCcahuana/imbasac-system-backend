package com.paulfernandosr.possystembackend.category.domain.port.output;

import com.paulfernandosr.possystembackend.category.domain.Category;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

public interface CategoryRepository {
    void create(Category category);

    Collection<Category> findAll();

    Optional<Category> findById(Long categoryId);

    boolean existsByName(String name);
}
