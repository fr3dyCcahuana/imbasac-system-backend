package com.paulfernandosr.possystembackend.category.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.category.domain.Category;
import com.paulfernandosr.possystembackend.category.domain.port.output.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PostgresCategoryRepository implements CategoryRepository {
    private final JdbcClient jdbcClient;

    @Override
    public void create(Category category) {
        String insertCategorySql = "INSERT INTO categories(name, description) VALUES(?, ?)";

        jdbcClient.sql(insertCategorySql)
                .params(category.getName(), category.getDescription())
                .update();
    }

    @Override
    public Collection<Category> findAll() {
        String selectAllCategoriesSql = "SELECT id, name, description FROM categories";

        return jdbcClient.sql(selectAllCategoriesSql)
                .query(Category.class)
                .list();
    }

    @Override
    public Optional<Category> findById(Long categoryId) {
        String selectCategoryByIdSql = "SELECT id, name, description FROM categories WHERE id = ?";

        return jdbcClient.sql(selectCategoryByIdSql)
                .param(categoryId)
                .query(Category.class)
                .optional();
    }

    @Override
    public boolean existsByName(String name) {
        String selectCategoryByNameSql = "SELECT EXISTS (SELECT 1 FROM categories WHERE name = ?)";

        return jdbcClient.sql(selectCategoryByNameSql)
                .param(name)
                .query(Boolean.class)
                .single();
    }
}
