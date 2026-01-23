package com.paulfernandosr.possystembackend.product.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.product.domain.Product;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductBulkUpsertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.HashSet;
import java.util.Set;
import java.util.StringJoiner;

@Repository
@RequiredArgsConstructor
public class PostgresProductBulkUpsertRepository implements ProductBulkUpsertRepository {

    private final JdbcClient jdbcClient;

    @Override
    public Set<String> findExistingSkus(Set<String> skus) {
        if (skus == null || skus.isEmpty()) return Set.of();

        StringJoiner joiner = new StringJoiner(",");
        for (int i = 0; i < skus.size(); i++) joiner.add("?");

        String sql = "SELECT sku FROM product WHERE sku IN (" + joiner + ")";

        return new HashSet<>(jdbcClient.sql(sql)
                .params(skus.toArray())
                .query(String.class)
                .list());
    }

    @Override
    public void upsertBySku(Product p) {
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
            VALUES (?, ?, 'BIEN', ?, ?, ?, FALSE, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE, TRUE, FALSE)
            ON CONFLICT (sku) DO UPDATE SET
                name = EXCLUDED.name,
                product_type = 'BIEN',
                category = EXCLUDED.category,
                presentation = EXCLUDED.presentation,
                factor = EXCLUDED.factor,
                manage_by_serial = FALSE,
                origin_type = EXCLUDED.origin_type,
                origin_country = EXCLUDED.origin_country,
                factory_code = EXCLUDED.factory_code,
                compatibility = EXCLUDED.compatibility,
                barcode = EXCLUDED.barcode,
                warehouse_location = EXCLUDED.warehouse_location,
                price_a = EXCLUDED.price_a,
                price_b = EXCLUDED.price_b,
                price_c = EXCLUDED.price_c,
                price_d = EXCLUDED.price_d,
                cost_reference = EXCLUDED.cost_reference,
                facturable_sunat = TRUE,
                affects_stock = TRUE,
                gift_allowed = FALSE
            """;

        jdbcClient.sql(sql).params(
                p.getSku(),
                p.getName(),
                p.getCategory(),
                p.getPresentation(),
                p.getFactor(),
                p.getOriginType(),
                p.getOriginCountry(),
                p.getFactoryCode(),
                p.getCompatibility(),
                p.getBarcode(),
                p.getWarehouseLocation(),
                p.getPriceA(),
                p.getPriceB(),
                p.getPriceC(),
                p.getPriceD(),
                p.getCostReference()
        ).update();
    }
}
