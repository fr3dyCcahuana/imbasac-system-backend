package com.paulfernandosr.possystembackend.manualpdf.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfDocument;
import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfFamily;
import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfModel;
import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfModelStorageContext;
import com.paulfernandosr.possystembackend.manualpdf.domain.port.output.ManualPdfRepository;
import com.paulfernandosr.possystembackend.manualpdf.infrastructure.adapter.output.mapper.ManualPdfDocumentRowMapper;
import com.paulfernandosr.possystembackend.manualpdf.infrastructure.adapter.output.mapper.ManualPdfFamilyRowMapper;
import com.paulfernandosr.possystembackend.manualpdf.infrastructure.adapter.output.mapper.ManualPdfModelRowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PostgresManualPdfRepository implements ManualPdfRepository {

    private final JdbcClient jdbcClient;

    @Override
    public List<Integer> findAvailableYears() {
        String sql = """
                select distinct gs.year_value
                from manual_pdf_document d
                cross join lateral generate_series(d.year_from, d.year_to) as gs(year_value)
                where d.enabled = true
                order by gs.year_value desc
                """;

        return jdbcClient.sql(sql)
                .query(Integer.class)
                .list();
    }

    @Override
    public List<ManualPdfFamily> findFamiliesByYear(int year) {
        String sql = """
                select x.id, x.code, x.name
                from (
                    select distinct f.id, f.code, f.name, f.sort_order
                    from manual_pdf_document d
                    join manual_pdf_model m on m.id = d.model_id
                    join manual_pdf_family f on f.id = m.family_id
                    where d.enabled = true
                      and m.enabled = true
                      and f.enabled = true
                      and :year between d.year_from and d.year_to
                ) x
                order by x.sort_order, x.name
                """;

        return jdbcClient.sql(sql)
                .param("year", year)
                .query(new ManualPdfFamilyRowMapper())
                .list();
    }

    @Override
    public List<ManualPdfModel> findModelsByYearAndFamily(int year, Long familyId) {
        String sql = """
                select x.id, x.family_id, x.code, x.name
                from (
                    select distinct m.id, m.family_id, m.code, m.name, m.sort_order
                    from manual_pdf_document d
                    join manual_pdf_model m on m.id = d.model_id
                    where d.enabled = true
                      and m.enabled = true
                      and m.family_id = :familyId
                      and :year between d.year_from and d.year_to
                ) x
                order by x.sort_order, x.name
                """;

        return jdbcClient.sql(sql)
                .param("year", year)
                .param("familyId", familyId)
                .query(new ManualPdfModelRowMapper())
                .list();
    }

    @Override
    public Optional<ManualPdfDocument> findBestDocumentByYearAndModel(int year, Long modelId) {
        String sql = """
                select d.id, d.model_id, d.title, d.year_from, d.year_to, d.file_name, d.file_key,
                       d.mime_type, d.file_size, d.enabled
                from manual_pdf_document d
                where d.enabled = true
                  and d.model_id = :modelId
                  and :year between d.year_from and d.year_to
                order by
                    case
                        when d.year_from = :year and d.year_to = :year then 0
                        else 1
                    end,
                    (d.year_to - d.year_from) asc,
                    d.updated_at desc,
                    d.id desc
                limit 1
                """;

        return jdbcClient.sql(sql)
                .param("year", year)
                .param("modelId", modelId)
                .query(new ManualPdfDocumentRowMapper())
                .optional();
    }

    @Override
    public Optional<ManualPdfDocument> findById(Long id) {
        String sql = """
                select d.id, d.model_id, d.title, d.year_from, d.year_to, d.file_name, d.file_key,
                       d.mime_type, d.file_size, d.enabled
                from manual_pdf_document d
                where d.id = :id
                  and d.enabled = true
                """;

        return jdbcClient.sql(sql)
                .param("id", id)
                .query(new ManualPdfDocumentRowMapper())
                .optional();
    }

    @Override
    public Optional<ManualPdfModelStorageContext> findModelStorageContextById(Long modelId) {
        String sql = """
                select m.id as model_id,
                       m.family_id,
                       f.code as family_code,
                       f.name as family_name,
                       m.code as model_code,
                       m.name as model_name,
                       f.enabled as family_enabled,
                       m.enabled as model_enabled
                from manual_pdf_model m
                join manual_pdf_family f on f.id = m.family_id
                where m.id = :modelId
                """;

        return jdbcClient.sql(sql)
                .param("modelId", modelId)
                .query((rs, rowNum) -> new ManualPdfModelStorageContext(
                        rs.getLong("model_id"),
                        rs.getLong("family_id"),
                        rs.getString("family_code"),
                        rs.getString("family_name"),
                        rs.getString("model_code"),
                        rs.getString("model_name"),
                        rs.getBoolean("family_enabled"),
                        rs.getBoolean("model_enabled")
                ))
                .optional();
    }

    @Override
    public boolean existsOverlappingDocument(Long modelId, Integer yearFrom, Integer yearTo) {
        String sql = """
                select exists(
                    select 1
                    from manual_pdf_document d
                    where d.model_id = :modelId
                      and d.enabled = true
                      and d.year_from <= :yearTo
                      and d.year_to >= :yearFrom
                )
                """;

        return Boolean.TRUE.equals(jdbcClient.sql(sql)
                .param("modelId", modelId)
                .param("yearFrom", yearFrom)
                .param("yearTo", yearTo)
                .query(Boolean.class)
                .single());
    }

    @Override
    public boolean familyExists(Long familyId) {
        String sql = """
                select exists(
                    select 1
                    from manual_pdf_family
                    where id = :familyId
                )
                """;

        return Boolean.TRUE.equals(jdbcClient.sql(sql)
                .param("familyId", familyId)
                .query(Boolean.class)
                .single());
    }

    @Override
    public ManualPdfFamily upsertFamily(String code, String name, Integer sortOrder, Boolean enabled) {
        String sql = """
                insert into manual_pdf_family (code, name, sort_order, enabled)
                values (:code, :name, :sortOrder, :enabled)
                on conflict (code)
                do update set
                    name = excluded.name,
                    sort_order = excluded.sort_order,
                    enabled = excluded.enabled,
                    updated_at = now()
                returning id, code, name
                """;

        return jdbcClient.sql(sql)
                .param("code", code)
                .param("name", name)
                .param("sortOrder", sortOrder != null ? sortOrder : 0)
                .param("enabled", enabled == null || enabled)
                .query(new ManualPdfFamilyRowMapper())
                .single();
    }

    @Override
    public ManualPdfModel upsertModel(Long familyId, String code, String name, Integer sortOrder, Boolean enabled) {
        String sql = """
                insert into manual_pdf_model (family_id, code, name, sort_order, enabled)
                values (:familyId, :code, :name, :sortOrder, :enabled)
                on conflict (family_id, code)
                do update set
                    name = excluded.name,
                    sort_order = excluded.sort_order,
                    enabled = excluded.enabled,
                    updated_at = now()
                returning id, family_id, code, name
                """;

        return jdbcClient.sql(sql)
                .param("familyId", familyId)
                .param("code", code)
                .param("name", name)
                .param("sortOrder", sortOrder != null ? sortOrder : 0)
                .param("enabled", enabled == null || enabled)
                .query(new ManualPdfModelRowMapper())
                .single();
    }

    @Override
    public ManualPdfDocument createDocument(ManualPdfDocument document) {
        String sql = """
                insert into manual_pdf_document (
                    model_id,
                    title,
                    year_from,
                    year_to,
                    file_name,
                    file_key,
                    mime_type,
                    file_size,
                    enabled
                )
                values (
                    :modelId,
                    :title,
                    :yearFrom,
                    :yearTo,
                    :fileName,
                    :fileKey,
                    :mimeType,
                    :fileSize,
                    :enabled
                )
                returning id, model_id, title, year_from, year_to, file_name, file_key, mime_type, file_size, enabled
                """;

        return jdbcClient.sql(sql)
                .param("modelId", document.modelId())
                .param("title", document.title())
                .param("yearFrom", document.yearFrom())
                .param("yearTo", document.yearTo())
                .param("fileName", document.fileName())
                .param("fileKey", document.fileKey())
                .param("mimeType", document.mimeType())
                .param("fileSize", document.fileSize())
                .param("enabled", document.enabled() != null ? document.enabled() : true)
                .query(new ManualPdfDocumentRowMapper())
                .single();
    }
}
