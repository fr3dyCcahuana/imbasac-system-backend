package com.paulfernandosr.possystembackend.product.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.product.domain.Product;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductBulkUpsertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class PostgresProductBulkUpsertRepository implements ProductBulkUpsertRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void upsertBySku(Product p) {
        String sql = """
                INSERT INTO product (
                    sku,
                    name,
                    product_type,
                    category,
                    brand,
                    model,
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
                ) VALUES (
                    ?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?
                )
                ON CONFLICT (sku) DO UPDATE
                SET
                    name = EXCLUDED.name,
                    product_type = EXCLUDED.product_type,
                    category = EXCLUDED.category,
                    brand = EXCLUDED.brand,
                    model = EXCLUDED.model,
                    presentation = EXCLUDED.presentation,
                    factor = EXCLUDED.factor,
                    manage_by_serial = EXCLUDED.manage_by_serial,
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
                    facturable_sunat = EXCLUDED.facturable_sunat,
                    affects_stock = EXCLUDED.affects_stock,
                    gift_allowed = EXCLUDED.gift_allowed,
                    updated_at = NOW();
                """;

        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql);
            int i = 1;
            ps.setString(i++, p.getSku());
            ps.setString(i++, p.getName());
            ps.setString(i++, p.getProductType());
            ps.setString(i++, p.getCategory());
            ps.setString(i++, p.getBrand());
            ps.setString(i++, p.getModel());
            ps.setString(i++, p.getPresentation());
            setBigDecimal(ps, i++, p.getFactor());
            ps.setBoolean(i++, Boolean.TRUE.equals(p.getManageBySerial()));
            ps.setString(i++, p.getOriginType());
            ps.setString(i++, p.getOriginCountry());
            ps.setString(i++, p.getFactoryCode());
            ps.setString(i++, p.getCompatibility());
            ps.setString(i++, p.getBarcode());
            ps.setString(i++, p.getWarehouseLocation());
            setBigDecimal(ps, i++, p.getPriceA());
            setBigDecimal(ps, i++, p.getPriceB());
            setBigDecimal(ps, i++, p.getPriceC());
            setBigDecimal(ps, i++, p.getPriceD());
            setBigDecimal(ps, i++, p.getCostReference());
            ps.setBoolean(i++, Boolean.TRUE.equals(p.getFacturableSunat()));
            ps.setBoolean(i++, Boolean.TRUE.equals(p.getAffectsStock()));
            ps.setBoolean(i++, Boolean.TRUE.equals(p.getGiftAllowed()));
            return ps;
        });
    }

    private void setBigDecimal(PreparedStatement ps, int idx, java.math.BigDecimal v) throws SQLException {
        if (v == null) ps.setNull(idx, java.sql.Types.NUMERIC);
        else ps.setBigDecimal(idx, v);
    }

    @Override
    public Set<String> findExistingSkus(Set<String> skus) {
        if (skus == null || skus.isEmpty()) return Set.of();

        String placeholders = String.join(",", skus.stream().map(s -> "?").toList());
        String sql = "SELECT sku FROM product WHERE sku IN (" + placeholders + ")";

        return new HashSet<>(jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString(1), skus.toArray()));
    }
}
