package com.paulfernandosr.possystembackend.product.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.product.domain.ProductImage;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductImageRepository;
import com.paulfernandosr.possystembackend.product.infrastructure.adapter.output.mapper.ProductImageRowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.StringJoiner;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PostgresProductImageRepository implements ProductImageRepository {

    private final JdbcClient jdbcClient;

    @Override
    public void create(ProductImage image) {
        // Si va a ser principal, primero desmarcamos las demás
        if (Boolean.TRUE.equals(image.getIsMain())) {
            String unsetMainSql = "UPDATE product_image SET is_main = FALSE WHERE product_id = ?";
            jdbcClient.sql(unsetMainSql)
                    .param(image.getProductId())
                    .update();
        }

        String sql = """
                INSERT INTO product_image(
                    product_id,
                    image_url,
                    position,
                    is_main
                )
                VALUES (?, ?, ?, ?)
                """;

        jdbcClient.sql(sql)
                .params(
                        image.getProductId(),
                        image.getImageUrl(),
                        image.getPosition(),
                        image.getIsMain() != null ? image.getIsMain() : Boolean.FALSE
                )
                .update();
    }

    @Override
    public Collection<ProductImage> findByProductId(Long productId) {
        String sql = """
                SELECT
                    id,
                    product_id,
                    image_url,
                    position,
                    is_main,
                    created_at
                FROM product_image
                WHERE product_id = ?
                ORDER BY position ASC, created_at ASC
                """;

        return jdbcClient.sql(sql)
                .param(productId)
                .query(new ProductImageRowMapper())
                .list();
    }

    @Override
    public Collection<ProductImage> findByProductIds(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }

        // Construimos placeholders dinámicos para evitar problemas con arrays/ANY en JdbcClient.
        StringJoiner joiner = new StringJoiner(",");
        for (int i = 0; i < productIds.size(); i++) {
            joiner.add("?");
        }

        String sql = """
                SELECT
                    id,
                    product_id,
                    image_url,
                    position,
                    is_main,
                    created_at
                FROM product_image
                WHERE product_id IN (""" + joiner + ")\n" +
                "ORDER BY product_id ASC, position ASC, created_at ASC";

        return jdbcClient.sql(sql)
                .params(productIds.toArray())
                .query(new ProductImageRowMapper())
                .list();
    }

    @Override
    public Optional<ProductImage> findByIdAndProductId(Long imageId, Long productId) {
        String sql = """
                SELECT
                    id,
                    product_id,
                    image_url,
                    position,
                    is_main,
                    created_at
                FROM product_image
                WHERE id = ?
                  AND product_id = ?
                """;

        return jdbcClient.sql(sql)
                .params(imageId, productId)
                .query(new ProductImageRowMapper())
                .optional();
    }

    @Override
    public void update(ProductImage image) {
        if (Boolean.TRUE.equals(image.getIsMain())) {
            String unsetMainSql = "UPDATE product_image SET is_main = FALSE WHERE product_id = ?";
            jdbcClient.sql(unsetMainSql)
                    .param(image.getProductId())
                    .update();
        }

        String sql = """
                UPDATE product_image
                   SET image_url = ?,
                       position  = ?,
                       is_main   = ?
                 WHERE id = ?
                   AND product_id = ?
                """;

        jdbcClient.sql(sql)
                .params(
                        image.getImageUrl(),
                        image.getPosition(),
                        image.getIsMain() != null ? image.getIsMain() : Boolean.FALSE,
                        image.getId(),
                        image.getProductId()
                )
                .update();
    }

    @Override
    public void deleteByIdAndProductId(Long imageId, Long productId) {
        String sql = "DELETE FROM product_image WHERE id = ? AND product_id = ?";
        jdbcClient.sql(sql)
                .params(imageId, productId)
                .update();
    }
}
