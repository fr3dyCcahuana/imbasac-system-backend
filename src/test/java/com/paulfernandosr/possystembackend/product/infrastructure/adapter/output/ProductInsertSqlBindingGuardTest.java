package com.paulfernandosr.possystembackend.product.infrastructure.adapter.output;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ProductInsertSqlBindingGuardTest {

    @Test
    void productCreateInsertColumnsMatchBindMarkers() throws Exception {
        SqlInsert insert = productInsertFrom("PostgresProductRepository.java");

        assertThat(insert.bindMarkers())
                .as("product create INSERT bind markers")
                .isEqualTo(insert.columns());
    }

    @Test
    void productBulkUpsertInsertColumnsMatchBindMarkers() throws Exception {
        SqlInsert insert = productInsertFrom("PostgresProductBulkUpsertRepository.java");

        assertThat(insert.bindMarkers())
                .as("product bulk upsert INSERT bind markers")
                .isEqualTo(insert.columns());
    }

    private static SqlInsert productInsertFrom(String fileName) throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/paulfernandosr/possystembackend/product/infrastructure/adapter/output",
                fileName
        ));

        int insertStart = source.indexOf("INSERT INTO product (");
        assertThat(insertStart)
                .as(fileName + " must contain INSERT INTO product")
                .isGreaterThanOrEqualTo(0);

        int columnStart = source.indexOf('(', insertStart);
        int columnEnd = matchingParen(source, columnStart);
        String columnsSql = source.substring(columnStart + 1, columnEnd);

        int valuesStart = source.indexOf("VALUES", columnEnd);
        assertThat(valuesStart)
                .as(fileName + " product INSERT must contain VALUES")
                .isGreaterThanOrEqualTo(0);

        int valuesParenStart = source.indexOf('(', valuesStart);
        int valuesParenEnd = matchingParen(source, valuesParenStart);
        String valuesSql = source.substring(valuesParenStart + 1, valuesParenEnd);

        return new SqlInsert(countColumns(columnsSql), countBindMarkers(valuesSql));
    }

    private static int countColumns(String columnsSql) {
        return (int) Arrays.stream(columnsSql.split(","))
                .map(String::trim)
                .filter(column -> !column.isBlank())
                .count();
    }

    private static int countBindMarkers(String valuesSql) {
        int count = 0;
        for (int i = 0; i < valuesSql.length(); i++) {
            if (valuesSql.charAt(i) == '?') {
                count++;
            }
        }
        return count;
    }

    private static int matchingParen(String text, int openParen) {
        assertThat(openParen)
                .as("opening parenthesis must exist")
                .isGreaterThanOrEqualTo(0);

        int depth = 0;
        for (int i = openParen; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '(') {
                depth++;
            } else if (ch == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }

        throw new IllegalArgumentException("No matching parenthesis found");
    }

    private record SqlInsert(int columns, int bindMarkers) {
    }
}
