package com.paulfernandosr.possystembackend.product.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.common.domain.Page;
import com.paulfernandosr.possystembackend.common.domain.Pageable;
import com.paulfernandosr.possystembackend.common.infrastructure.mapper.QueryMapper;
import com.paulfernandosr.possystembackend.product.domain.ProductImage;
import com.paulfernandosr.possystembackend.product.domain.ProductSalesDetail;
import com.paulfernandosr.possystembackend.product.domain.ProductSerialUnit;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductSalesDetailRepository;
import com.paulfernandosr.possystembackend.product.infrastructure.adapter.output.mapper.ProductImageRowMapper;
import com.paulfernandosr.possystembackend.product.infrastructure.adapter.output.mapper.ProductSerialUnitRowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class PostgresProductSalesDetailRepository implements ProductSalesDetailRepository {

    private final JdbcClient jdbcClient;

    @Override
    public Page<ProductSalesDetail> findPage(
            String query,
            String category,
            boolean onlyWithStock,
            String priceList,
            String context,
            Pageable pageable
    ) {
        String like = QueryMapper.formatAsLikeParam(query);
        String cat = category == null ? "" : category.trim();
        String pl = priceList == null ? "A" : priceList.trim().toUpperCase();
        String ctx = context == null ? "PROFORMA" : context.trim().toUpperCase();

        String countSql = """
                WITH base AS (
                  SELECT
                    p.id,
                    p.affects_stock,
                    CASE
                      WHEN p.manage_by_serial = TRUE
                       AND UPPER(COALESCE(p.category, '')) IN ('MOTOR','MOTOCICLETAS')
                      THEN TRUE ELSE FALSE
                    END AS manage_by_serial,
                    CASE
                      WHEN (p.manage_by_serial = TRUE
                       AND UPPER(COALESCE(p.category, '')) IN ('MOTOR','MOTOCICLETAS')) THEN (
                        SELECT COUNT(1)
                          FROM product_serial_unit psu
                         WHERE psu.product_id = p.id
                           AND psu.status = 'EN_ALMACEN'
                      )
                      ELSE COALESCE(ps.quantity_on_hand, 0)
                    END AS stock_available
                  FROM product p
                  LEFT JOIN product_stock ps ON ps.product_id = p.id
                  WHERE (p.sku ILIKE ? OR p.barcode ILIKE ? OR p.name ILIKE ?)
                    AND (? = '' OR p.category = ?)
                    AND (? <> 'SALE' OR p.facturable_sunat = TRUE)
                )
                SELECT COUNT(1)
                  FROM base
                 WHERE (? = FALSE OR affects_stock = FALSE OR stock_available > 0)
                """;

        long totalElements = jdbcClient.sql(countSql)
                .params(
                        like, like, like,
                        cat, cat,
                        ctx,
                        onlyWithStock
                )
                .query(Long.class)
                .single();

        String pageSql = """
                WITH base AS (
                  SELECT
                    p.id,
                    p.sku,
                    p.barcode,
                    p.name,
                    p.category,
                    p.presentation,
                    p.factor,
                
                    CASE
                      WHEN p.manage_by_serial = TRUE
                       AND UPPER(COALESCE(p.category, '')) IN ('MOTOR','MOTOCICLETAS')
                      THEN TRUE ELSE FALSE
                    END AS manage_by_serial,
                    p.compatibility,
                    p.gift_allowed,
                
                    p.affects_stock,
                    p.facturable_sunat,
                
                    CASE ?
                      WHEN 'A' THEN p.price_a
                      WHEN 'B' THEN p.price_b
                      WHEN 'C' THEN p.price_c
                      WHEN 'D' THEN p.price_d
                      ELSE p.price_a
                    END AS unit_price,
                
                    CASE
                      WHEN (p.manage_by_serial = TRUE
                       AND UPPER(COALESCE(p.category, '')) IN ('MOTOR','MOTOCICLETAS')) THEN (
                        SELECT COUNT(1)
                          FROM product_serial_unit psu
                         WHERE psu.product_id = p.id
                           AND psu.status = 'EN_ALMACEN'
                      )
                      ELSE COALESCE(ps.quantity_on_hand, 0)
                    END AS stock_available
                
                  FROM product p
                  LEFT JOIN product_stock ps ON ps.product_id = p.id
                  WHERE (p.sku ILIKE ? OR p.barcode ILIKE ? OR p.name ILIKE ?)
                    AND (? = '' OR p.category = ?)
                    AND (? <> 'SALE' OR p.facturable_sunat = TRUE)
                )
                SELECT
                  id, sku, barcode, name, category, presentation, factor,
                  manage_by_serial, compatibility, gift_allowed,
                  affects_stock, facturable_sunat,
                  unit_price,
                  stock_available
                FROM base
                WHERE (? = FALSE OR affects_stock = FALSE OR stock_available > 0)
                ORDER BY name ASC
                LIMIT ?
                OFFSET ?
                """;

        int size = pageable.getSize();
        int page = pageable.getNumber();

        List<ProductSalesDetail> content = jdbcClient.sql(pageSql)
                .params(
                        pl,
                        like, like, like,
                        cat, cat,
                        ctx,
                        onlyWithStock,
                        size,
                        page * size
                )
                .query((rs, rowNum) -> ProductSalesDetail.builder()
                        .id(rs.getLong("id"))
                        .sku(rs.getString("sku"))
                        .barcode(rs.getString("barcode"))
                        .name(rs.getString("name"))
                        .category(rs.getString("category"))
                        .presentation(rs.getString("presentation"))
                        .factor(rs.getBigDecimal("factor"))

                        .manageBySerial((Boolean) rs.getObject("manage_by_serial"))
                        .compatibility(rs.getString("compatibility"))
                        .giftAllowed((Boolean) rs.getObject("gift_allowed"))

                        .affectsStock((Boolean) rs.getObject("affects_stock"))
                        .facturableSunat((Boolean) rs.getObject("facturable_sunat"))

                        .priceList(pl)
                        .price(rs.getBigDecimal("unit_price"))

                        // ✅ NUEVO: stock disponible calculado
                        .stockAvailable(rs.getBigDecimal("stock_available"))

                        .images(List.of())
                        .availableSerialUnits(List.of())
                        .build())
                .list();

        if (content.isEmpty()) {
            return Page.<ProductSalesDetail>builder()
                    .content(List.of())
                    .number(page)
                    .size(size)
                    .numberOfElements(0)
                    .totalElements(totalElements)
                    .totalPages((int) Math.ceil((double) totalElements / size))
                    .build();
        }

        // 3) BATCH: Images
        List<Long> productIds = content.stream().map(ProductSalesDetail::getId).toList();
        String imgPlaceholders = productIds.stream().map(x -> "?").collect(Collectors.joining(","));

        String imagesSql = """
                SELECT
                  id,
                  product_id,
                  image_url,
                  position,
                  is_main,
                  created_at
                FROM product_image
                WHERE product_id IN ("""
                + imgPlaceholders +
                """
                        )
                        ORDER BY product_id ASC, is_main DESC, position ASC, created_at ASC
                        """;

        Map<Long, List<ProductImage>> imagesByProduct = jdbcClient.sql(imagesSql)
                .params(productIds.toArray())
                .query(new ProductImageRowMapper())
                .list()
                .stream()
                .collect(Collectors.groupingBy(
                        ProductImage::getProductId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        // 4) BATCH: SerialUnits disponibles
        List<Long> serialProductIds = content.stream()
                .filter(p -> Boolean.TRUE.equals(p.getManageBySerial()))
                .map(ProductSalesDetail::getId)
                .toList();

        Map<Long, List<ProductSerialUnit>> serialsByProduct = new HashMap<>();
        if (!serialProductIds.isEmpty()) {
            String psuPlaceholders = serialProductIds.stream().map(x -> "?").collect(Collectors.joining(","));

            String serialSql = """
                    SELECT
                      id AS serial_unit_id,
                      product_id,
                      purchase_item_id,
                      sale_item_id,
                      stock_adjustment_id,
                      vin,
                      chassis_number,
                      engine_number,
                      color,
                      year_make,
                      dua_number,
                      dua_item,
                      status,
                      created_at,
                      updated_at
                    FROM product_serial_unit
                    WHERE product_id IN ("""
                    + psuPlaceholders +
                    """
                              )
                              AND status = 'EN_ALMACEN'
                            ORDER BY product_id ASC, created_at ASC
                            """;

            serialsByProduct = jdbcClient.sql(serialSql)
                    .params(serialProductIds.toArray())
                    .query(new ProductSerialUnitRowMapper())
                    .list()
                    .stream()
                    .collect(Collectors.groupingBy(
                            ProductSerialUnit::getProductId,
                            LinkedHashMap::new,
                            Collectors.toList()
                    ));
        }

        // 5) Enlazar
        for (ProductSalesDetail dto : content) {
            dto.setImages(imagesByProduct.getOrDefault(dto.getId(), List.of()));
            dto.setAvailableSerialUnits(serialsByProduct.getOrDefault(dto.getId(), List.of()));
        }

        int totalPages = (int) Math.ceil((double) totalElements / size);

        return Page.<ProductSalesDetail>builder()
                .content(content)
                .number(page)
                .size(size)
                .numberOfElements(content.size())
                .totalElements(totalElements)
                .totalPages(totalPages)
                .build();
    }
}
