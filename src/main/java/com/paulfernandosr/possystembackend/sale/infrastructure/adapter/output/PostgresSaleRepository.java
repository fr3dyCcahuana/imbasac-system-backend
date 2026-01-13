package com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.common.domain.Page;
import com.paulfernandosr.possystembackend.common.domain.Pageable;
import com.paulfernandosr.possystembackend.common.infrastructure.mapper.QueryMapper;
import com.paulfernandosr.possystembackend.sale.domain.Sale;
import com.paulfernandosr.possystembackend.sale.domain.SaleItem;
import com.paulfernandosr.possystembackend.sale.domain.SaleType;
import com.paulfernandosr.possystembackend.sale.domain.port.output.SaleRepository;
//import com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.mapper.FullSaleItemRowMapper;
import com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.mapper.SaleItemRowMapper;
import com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.mapper.SaleRowMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class PostgresSaleRepository implements SaleRepository {
    private final JdbcClient jdbcClient;

    @Override
    @Transactional
    public void create(Sale sale) {
        KeyHolder saleKeyHolder = new GeneratedKeyHolder();

        String insertSaleSql = """
                    INSERT INTO sales(
                        customer_id,
                        number,
                        type,
                        discount,
                        comment,
                        status,
                        issued_at,
                        issued_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

        jdbcClient.sql(insertSaleSql)
                .params(sale.getCustomer().getId(),
                        sale.getNumber(),
                        sale.getType().toString(),
                        sale.getDiscount(),
                        sale.getComment(),
                        sale.getStatus().toString(),
                        sale.getIssuedAt(),
                        sale.getIssuedBy().getId())
                .update(saleKeyHolder, "id");

        long saleId = Optional.ofNullable(saleKeyHolder.getKey())
                .map(Number::longValue)
                .orElseThrow();

        sale.setId(saleId);

        String insertSaleItemsSql = """
                    INSERT INTO sale_items(
                        sale_id,
                        product_id,
                        price,
                        quantity)
                    VALUES (?, ?, ?, ?)
                """;

        sale.getItems().forEach(item -> {
            jdbcClient.sql(insertSaleItemsSql)
                    .params(saleId, item.getProduct().getId(), item.getPrice(), item.getQuantity())
                    .update();

//            String updateProductQuantitySql = """
//                        UPDATE products
//                        SET stock = stock - ?
//                        WHERE id = ?
//                    """;
//
//            jdbcClient.sql(updateProductQuantitySql)
//                    .params(item.getQuantity(), item.getProduct().getId())
//                    .update();
        });

        BigDecimal totalIncome = sale.getItems()
                .stream()
                .map(saleItem -> saleItem.getPrice().multiply(new BigDecimal(saleItem.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        String updateSaleSessionByIssuedBySql = """
                    UPDATE sale_sessions
                    SET sales_income = sales_income + ?,
                        total_discount = total_discount + ?
                    WHERE user_id = ?
                    AND closed_at IS NULL
                """;

        jdbcClient.sql(updateSaleSessionByIssuedBySql)
                .params(totalIncome,
                        sale.getDiscount(),
                        sale.getIssuedBy().getId())
                .update();
    }

    @Override
    public Optional<Sale> findById(Long saleId) {
        String selectSaleByIdSql = """
                    SELECT
                        s.id AS sale_id,
                        s.number,
                        s.type,
                        s.discount,
                        s.comment,
                        s.status,
                        s.issued_at,
                        c.id AS customer_id,
                        c.legal_name AS customer_name,
                        c.document_type AS customer_document_type,
                        c.document_number AS customer_document_number,
                        c.address AS customer_address,
                        c.enabled AS customer_enabled,
                        u.id AS user_id,
                        u.first_name AS user_first_name,
                        u.last_name AS user_last_name,
                        u.username AS username,
                        r.id AS role_id,
                        r.name AS role_name,
                        r.description AS role_description
                    FROM sales s
                    INNER JOIN customers c ON s.customer_id = c.id
                    INNER JOIN users u ON s.issued_by = u.id
                    INNER JOIN roles r ON r.id = u.role_id
                    WHERE s.id = ?;
                """;

        return jdbcClient.sql(selectSaleByIdSql)
                .param(saleId)
                .query(new SaleRowMapper())
                .optional();
    }

/*    @Override
    public Collection<SaleItem> findFullSaleItemsBySaleId(Long saleId) {

        String selectSaleItemsBySaleIdSql = """
                    SELECT
                        p.id AS product_id,
                        p.name AS product_name,
                        p.description AS product_description,
                        p.origin_code AS product_origin_code,
                        p.barcode AS product_barcode,
                        c.id AS category_id,
                        c.name AS category_name,
                        c.description AS category_description,
                        si.price AS sale_item_price,
                        si.quantity AS sale_item_quantity
                    FROM sale_items si
                    INNER JOIN products p ON si.product_id = p.id
                    INNER JOIN categories c ON p.category_id = c.id
                    WHERE si.sale_id = ?;
                """;

        return jdbcClient.sql(selectSaleItemsBySaleIdSql)
                .param(saleId)
                .query(new FullSaleItemRowMapper())
                .list();
    }*/

    @Override
    public Collection<SaleItem> findSaleItemsBySaleId(Long saleId) {
        String selectSaleItemsBySaleIdSql = """
                    SELECT
                        si.price AS sale_item_price,
                        si.quantity AS sale_item_quantity
                    FROM sale_items si
                    WHERE si.sale_id = ?;
                """;

        return jdbcClient.sql(selectSaleItemsBySaleIdSql)
                .param(saleId)
                .query(new SaleItemRowMapper())
                .list();
    }

    @Override
    public Long getNextNumberByType(SaleType saleType) {
        String selectSequentialSql = saleType.isReceipt()
                ? "SELECT NEXTVAL('receipt_sequential')"
                : "SELECT NEXTVAL('invoice_sequential')";

        return jdbcClient.sql(selectSequentialSql)
                .query(Long.class)
                .single();
    }

    @Override
    public Page<Sale> findPage(String query, String type, Pageable pageable) {
        // base FROM + JOIN
        final String fromJoin = """
        FROM sales s
        INNER JOIN customers c ON s.customer_id = c.id
        INNER JOIN users u ON s.issued_by = u.id
        INNER JOIN roles r ON u.role_id = r.id
        """;

        // WHERE dinámico
        StringBuilder where = new StringBuilder("WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        // Filtro por texto (cliente) si viene query
        if (query != null && !query.isBlank()) {
            where.append("AND (c.legal_name ILIKE ? OR c.document_number ILIKE ?) ");
            params.add(QueryMapper.formatAsLikeParam(query));
            params.add(QueryMapper.formatAsLikeParam(query));
        }

        // Filtro por type (opcional). Si 'type' es null/blank, no se aplica.
        if (type != null && !type.isBlank()) {
            where.append("AND s.type = ? ");
            params.add(type);
        }

        // ----- COUNT con los mismos filtros -----
        String countSql = "SELECT COUNT(1) " + fromJoin + where;
        long totalElements = jdbcClient.sql(countSql)
                .params(params.toArray())
                .query(Long.class)
                .single();

        // ----- SELECT page -----
        String selectSql = """
        SELECT
            s.id AS sale_id,
            s.number,
            s.type,
            s.discount,
            s.comment,
            s.status,
            s.issued_at,
            c.id AS customer_id,
            c.legal_name AS customer_name,
            c.document_type AS customer_document_type,
            c.document_number AS customer_document_number,
            c.address AS customer_address,
            c.enabled AS customer_enabled,
            u.id AS user_id,
            u.first_name AS user_first_name,
            u.last_name AS user_last_name,
            u.username AS username,
            r.id AS role_id,
            r.name AS role_name,
            r.description AS role_description
        """ + fromJoin + where + """
        ORDER BY s.issued_at DESC
        LIMIT ? OFFSET ?
        """;

        int pageSize = pageable.getSize();
        int pageNumber = pageable.getNumber();

        // params para la página (copiamos los del WHERE y agregamos paginación)
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageSize);
        pageParams.add(pageNumber * pageSize);

        Collection<Sale> sales = jdbcClient.sql(selectSql)
                .params(pageParams.toArray())
                .query(new SaleRowMapper())
                .list();

        BigDecimal totalPages = BigDecimal.valueOf(totalElements)
                .divide(BigDecimal.valueOf(pageSize), 0, RoundingMode.CEILING);

        return Page.<Sale>builder()
                .content(sales)
                .number(pageNumber)
                .size(pageSize)
                .numberOfElements(sales.size())
                .totalPages(totalPages.intValue())
                .totalElements(totalElements)
                .build();
    }

    @Override
    @Transactional
    public void cancel(Sale sale) {
        String updateSaleStatusByIdSql = """
                    UPDATE sales
                    SET status = ?,
                        cancelled_at = ?,
                        cancelled_by = ?
                    WHERE id = ?
                """;

        jdbcClient.sql(updateSaleStatusByIdSql)
                .params(sale.getStatus().toString(),
                        sale.getIssuedAt(),
                        sale.getIssuedBy().getId(),
                        sale.getId())
                .update();

/*        String updateProductQuantitySql = """
                    UPDATE products
                    SET stock = stock + ?
                    WHERE id = ?
                """;

        sale.getItems().forEach(saleItem -> {
            jdbcClient.sql(updateProductQuantitySql)
                    .params(saleItem.getQuantity(), saleItem.getProduct().getId())
                    .update();
        });*/

        BigDecimal totalExpenses = sale.getItems()
                .stream()
                .map(saleItem -> saleItem.getPrice().multiply(new BigDecimal(saleItem.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .subtract(sale.getDiscount())
                .setScale(2, RoundingMode.HALF_UP);

        String updateSaleSessionByIssuedBySql = """
                    UPDATE sale_sessions
                    SET total_expenses = total_expenses + ?
                    WHERE user_id = ?
                    AND closed_at IS NULL
                """;

        jdbcClient.sql(updateSaleSessionByIssuedBySql)
                .params(totalExpenses,
                        sale.getIssuedBy().getId())
                .update();
    }
}
