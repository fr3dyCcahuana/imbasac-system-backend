package com.paulfernandosr.possystembackend.product.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.common.domain.Page;
import com.paulfernandosr.possystembackend.common.domain.Pageable;
import com.paulfernandosr.possystembackend.common.infrastructure.mapper.QueryMapper;
import com.paulfernandosr.possystembackend.product.domain.Product;
import com.paulfernandosr.possystembackend.product.domain.exception.InvalidProductException;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductRepository;
import com.paulfernandosr.possystembackend.product.infrastructure.adapter.output.mapper.ProductRowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class PostgresProductRepository implements ProductRepository {

    private final JdbcClient jdbcClient;

    @Override
    public Product create(Product product) {
        String sql = """
            INSERT INTO product(
                sku,
                name,
                product_type,
                category,
                presentation,
                factor,
                manage_by_serial,
                origin_type,
                origin_country,
                factory_code,
                compatibility,
                barcode,
                warehouse_location,
                price_a,
                price_b,
                price_c,
                price_d,
                cost_reference,
                facturable_sunat,
                affects_stock,
                gift_allowed
            )
            VALUES (?, ?, ?, ?, ?, ?, COALESCE(?, FALSE), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, COALESCE(?, TRUE), COALESCE(?, TRUE), COALESCE(?, FALSE))
            RETURNING
                id          AS product_id,
                sku,
                name,
                product_type,
                category,
                presentation,
                factor,
                manage_by_serial,
                origin_type,
                origin_country,
                factory_code,
                compatibility,
                barcode,
                warehouse_location,
                price_a,
                price_b,
                price_c,
                price_d,
                cost_reference,
                facturable_sunat,
                affects_stock,
                gift_allowed,
                -- ✅ stock real (on hand)
                CASE
                  WHEN manage_by_serial = TRUE THEN
                    COALESCE((
                      SELECT COUNT(*)::numeric(14,3)
                      FROM product_serial_unit su
                      WHERE su.product_id = id
                        AND su.status = 'EN_ALMACEN'
                    ), 0)
                  ELSE
                    COALESCE((
                      SELECT ps.quantity_on_hand
                      FROM product_stock ps
                      WHERE ps.product_id = id
                    ), 0)
                END AS stock_on_hand,
                created_at,
                updated_at
            """;

        try {
            return jdbcClient.sql(sql)
                    .params(
                            product.getSku(),
                            product.getName(),
                            product.getProductType(),
                            product.getCategory(),
                            product.getPresentation(),
                            product.getFactor(),
                            product.getManageBySerial(),
                            product.getOriginType(),
                            product.getOriginCountry(),
                            product.getFactoryCode(),
                            product.getCompatibility(),
                            product.getBarcode(),
                            product.getWarehouseLocation(),
                            product.getPriceA(),
                            product.getPriceB(),
                            product.getPriceC(),
                            product.getPriceD(),
                            product.getCostReference(),
                            product.getFacturableSunat(),
                            product.getAffectsStock(),
                            product.getGiftAllowed()
                    )
                    .query(new ProductRowMapper())
                    .single();
        } catch (DataIntegrityViolationException ex) {
            String message = ex.getMostSpecificCause() != null
                    ? ex.getMostSpecificCause().getMessage()
                    : ex.getMessage();

            // Puede variar el nombre del constraint en Postgres; cubrimos ambos casos comunes.
            if (message != null && (message.contains("product_sku_key") || message.contains("uq_product_sku") || message.contains("sku"))) {
                throw new InvalidProductException(
                        "Ya existe un producto registrado con el SKU: " + product.getSku()
                );
            }
            throw ex;
        }
    }

    @Override
    public Page<Product> findPage(String query, Pageable pageable) {
        String likeParam = QueryMapper.formatAsLikeParam(query);

        String countSql = """
            SELECT COUNT(1)
              FROM product p
             WHERE p.sku ILIKE ?
                OR p.barcode ILIKE ?
                OR p.name ILIKE ?
            """;

        long totalElements = jdbcClient.sql(countSql)
                .params(likeParam, likeParam, likeParam)
                .query(Long.class)
                .single();

        String selectSql = """
            SELECT
                p.id          AS product_id,
                p.sku,
                p.name,
                p.product_type,
                p.category,
                p.presentation,
                p.factor,
                p.manage_by_serial,
                p.origin_type,
                p.origin_country,
                p.factory_code,
                p.compatibility,
                p.barcode,
                p.warehouse_location,
                p.price_a,
                p.price_b,
                p.price_c,
                p.price_d,
                p.cost_reference,
                p.facturable_sunat,
                p.affects_stock,
                p.gift_allowed,
                -- ✅ stock real (on hand)
                CASE
                  WHEN p.manage_by_serial = TRUE THEN COALESCE(su_agg.serial_qty, 0)
                  ELSE COALESCE(ps.quantity_on_hand, 0)
                END AS stock_on_hand,
                p.created_at,
                p.updated_at
            FROM product p
            LEFT JOIN product_stock ps
                   ON ps.product_id = p.id
            LEFT JOIN (
              SELECT
                product_id,
                COUNT(*)::numeric(14,3) AS serial_qty
              FROM product_serial_unit
              WHERE status = 'EN_ALMACEN'
              GROUP BY product_id
            ) su_agg
                   ON su_agg.product_id = p.id
            WHERE p.sku ILIKE ?
               OR p.barcode ILIKE ?
               OR p.name ILIKE ?
            ORDER BY p.name ASC
            LIMIT ?
            OFFSET ?
            """;

        int pageSize = pageable.getSize();
        int pageNumber = pageable.getNumber();

        List<Product> products = jdbcClient.sql(selectSql)
                .params(
                        likeParam,
                        likeParam,
                        likeParam,
                        pageSize,
                        pageNumber * pageSize
                )
                .query(new ProductRowMapper())
                .list();

        BigDecimal totalPages = BigDecimal.valueOf(totalElements)
                .divide(BigDecimal.valueOf(pageSize), 0, RoundingMode.CEILING);

        return Page.<Product>builder()
                .content(products)
                .number(pageNumber)
                .size(pageSize)
                .numberOfElements(products.size())
                .totalPages(totalPages.intValue())
                .totalElements(totalElements)
                .build();
    }

    @Override
    public Optional<Product> findById(Long productId) {
        String sql = """
            SELECT
                p.id          AS product_id,
                p.sku,
                p.name,
                p.product_type,
                p.category,
                p.presentation,
                p.factor,
                p.manage_by_serial,
                p.origin_type,
                p.origin_country,
                p.factory_code,
                p.compatibility,
                p.barcode,
                p.warehouse_location,
                p.price_a,
                p.price_b,
                p.price_c,
                p.price_d,
                p.cost_reference,
                p.facturable_sunat,
                p.affects_stock,
                p.gift_allowed,
                -- ✅ stock real (on hand)
                CASE
                  WHEN p.manage_by_serial = TRUE THEN COALESCE(su_agg.serial_qty, 0)
                  ELSE COALESCE(ps.quantity_on_hand, 0)
                END AS stock_on_hand,
                p.created_at,
                p.updated_at
            FROM product p
            LEFT JOIN product_stock ps
                   ON ps.product_id = p.id
            LEFT JOIN (
              SELECT
                product_id,
                COUNT(*)::numeric(14,3) AS serial_qty
              FROM product_serial_unit
              WHERE status = 'EN_ALMACEN'
              GROUP BY product_id
            ) su_agg
                   ON su_agg.product_id = p.id
            WHERE p.id = ?
            """;

        return jdbcClient.sql(sql)
                .param(productId)
                .query(new ProductRowMapper())
                .optional();
    }

    @Override
    public Collection<Product> findByIdIn(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }

        String placeholders = productIds.stream()
                .map(id -> "?")
                .collect(Collectors.joining(","));

        String sql = """
            SELECT
                p.id          AS product_id,
                p.sku,
                p.name,
                p.product_type,
                p.category,
                p.presentation,
                p.factor,
                p.manage_by_serial,
                p.origin_type,
                p.origin_country,
                p.factory_code,
                p.compatibility,
                p.barcode,
                p.warehouse_location,
                p.price_a,
                p.price_b,
                p.price_c,
                p.price_d,
                p.cost_reference,
                p.facturable_sunat,
                p.affects_stock,
                p.gift_allowed,
                -- ✅ stock real (on hand)
                CASE
                  WHEN p.manage_by_serial = TRUE THEN COALESCE(su_agg.serial_qty, 0)
                  ELSE COALESCE(ps.quantity_on_hand, 0)
                END AS stock_on_hand,
                p.created_at,
                p.updated_at
            FROM product p
            LEFT JOIN product_stock ps
                   ON ps.product_id = p.id
            LEFT JOIN (
              SELECT
                product_id,
                COUNT(*)::numeric(14,3) AS serial_qty
              FROM product_serial_unit
              WHERE status = 'EN_ALMACEN'
              GROUP BY product_id
            ) su_agg
                   ON su_agg.product_id = p.id
            WHERE p.id IN (""" + placeholders + ")";

        return jdbcClient.sql(sql)
                .params(productIds.toArray())
                .query(new ProductRowMapper())
                .list();
    }

    @Override
    public void updateById(Long productId, Product product) {
        String sql = """
            UPDATE product
               SET sku                = ?,
                   name               = ?,
                   product_type       = ?,
                   category           = ?,
                   presentation       = ?,
                   factor             = ?,
                   manage_by_serial   = COALESCE(?, manage_by_serial),
                   origin_type        = ?,
                   origin_country     = ?,
                   factory_code       = ?,
                   compatibility      = ?,
                   barcode            = ?,
                   warehouse_location = ?,
                   price_a            = ?,
                   price_b            = ?,
                   price_c            = ?,
                   price_d            = ?,
                   cost_reference     = ?,
                   facturable_sunat   = COALESCE(?, facturable_sunat),
                   affects_stock      = COALESCE(?, affects_stock),
                   gift_allowed       = COALESCE(?, gift_allowed),
                   updated_at         = NOW()
             WHERE id = ?
            """;

        jdbcClient.sql(sql)
                .params(
                        product.getSku(),
                        product.getName(),
                        product.getProductType(),
                        product.getCategory(),
                        product.getPresentation(),
                        product.getFactor(),
                        product.getManageBySerial(),
                        product.getOriginType(),
                        product.getOriginCountry(),
                        product.getFactoryCode(),
                        product.getCompatibility(),
                        product.getBarcode(),
                        product.getWarehouseLocation(),
                        product.getPriceA(),
                        product.getPriceB(),
                        product.getPriceC(),
                        product.getPriceD(),
                        product.getCostReference(),
                        product.getFacturableSunat(),
                        product.getAffectsStock(),
                        product.getGiftAllowed(),
                        productId
                )
                .update();
    }
}
