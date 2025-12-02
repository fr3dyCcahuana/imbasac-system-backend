package com.paulfernandosr.possystembackend.category.infrastructure.adapter.output.helper;

import com.paulfernandosr.possystembackend.category.domain.exception.CategoryNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CategoryQueryHelper {
    private final JdbcClient jdbcClient;

    public long getCategoryIdById(String categoryId) {
        String selectCategoryByIdSql = "SELECT id FROM categories WHERE name = ?";

        return jdbcClient.sql(selectCategoryByIdSql)
                .param(categoryId)
                .query(Long.class)
                .optional()
                .orElseThrow(() -> new CategoryNotFoundException("Category not found with identification: " + categoryId));
    }
}
