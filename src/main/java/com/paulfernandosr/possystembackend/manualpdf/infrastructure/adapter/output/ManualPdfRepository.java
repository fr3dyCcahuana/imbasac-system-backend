package com.paulfernandosr.possystembackend.manualpdf.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfDocument;
import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfFamily;
import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfImage;
import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfModel;
import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfModelSummary;
import com.paulfernandosr.possystembackend.manualpdf.domain.exception.ManualPdfNotFoundException;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ManualPdfRepository {

    private final JdbcClient jdbcClient;

    private final RowMapper<ManualPdfFamily> familyRowMapper = (rs, rowNum) -> new ManualPdfFamily(
            rs.getLong("id"),
            rs.getString("code"),
            rs.getString("name"),
            rs.getInt("sort_order")
    );

    private final RowMapper<ManualPdfModel> modelRowMapper = (rs, rowNum) -> new ManualPdfModel(
            rs.getLong("id"),
            rs.getLong("family_id"),
            rs.getString("code"),
            rs.getString("name"),
            rs.getInt("sort_order")
    );

    private final RowMapper<ManualPdfDocument> documentRowMapper = (rs, rowNum) -> new ManualPdfDocument(
            rs.getLong("id"),
            rs.getLong("model_id"),
            rs.getString("title"),
            rs.getInt("year_from"),
            rs.getInt("year_to"),
            rs.getString("file_name"),
            rs.getString("file_key"),
            rs.getString("mime_type"),
            rs.getLong("file_size"),
            rs.getBoolean("enabled")
    );

    private final RowMapper<ManualPdfImage> imageRowMapper = (rs, rowNum) -> new ManualPdfImage(
            rs.getLong("id"),
            rs.getLong("document_id"),
            rs.getString("file_name"),
            rs.getString("file_key"),
            rs.getString("mime_type"),
            rs.getLong("file_size"),
            rs.getInt("sort_order"),
            rs.getBoolean("enabled")
    );

    private final RowMapper<ManualPdfModelSummary> summaryRowMapper = (rs, rowNum) -> new ManualPdfModelSummary(
            rs.getLong("model_id"),
            rs.getString("model_code"),
            rs.getString("model_name"),
            rs.getLong("family_id"),
            rs.getString("family_code"),
            rs.getString("family_name")
    );

    public Optional<ManualPdfFamily> findFamilyByCode(String code) {
        String sql = """
                select id, code, name, sort_order
                from manual_pdf_family
                where code = :code
                """;
        return jdbcClient.sql(sql).param("code", code).query(familyRowMapper).optional();
    }

    public Optional<ManualPdfFamily> findFamilyById(Long familyId) {
        String sql = """
                select id, code, name, sort_order
                from manual_pdf_family
                where id = :familyId
                """;
        return jdbcClient.sql(sql).param("familyId", familyId).query(familyRowMapper).optional();
    }

    public ManualPdfFamily insertFamily(String code, String name, Integer sortOrder) {
        String sql = """
                insert into manual_pdf_family (code, name, sort_order)
                values (:code, :name, :sortOrder)
                returning id, code, name, sort_order
                """;
        return jdbcClient.sql(sql)
                .param("code", code)
                .param("name", name)
                .param("sortOrder", sortOrder)
                .query(familyRowMapper)
                .single();
    }

    public Optional<ManualPdfModel> findModelByFamilyAndCode(Long familyId, String code) {
        String sql = """
                select id, family_id, code, name, sort_order
                from manual_pdf_model
                where family_id = :familyId
                  and code = :code
                """;
        return jdbcClient.sql(sql)
                .param("familyId", familyId)
                .param("code", code)
                .query(modelRowMapper)
                .optional();
    }

    public Optional<ManualPdfModel> findModelById(Long modelId) {
        String sql = """
                select id, family_id, code, name, sort_order
                from manual_pdf_model
                where id = :modelId
                  and enabled = true
                """;
        return jdbcClient.sql(sql).param("modelId", modelId).query(modelRowMapper).optional();
    }

    public ManualPdfModel insertModel(Long familyId, String code, String name, String normalizedName, Integer sortOrder) {
        String sql = """
                insert into manual_pdf_model (family_id, code, name, normalized_name, sort_order)
                values (:familyId, :code, :name, :normalizedName, :sortOrder)
                returning id, family_id, code, name, sort_order
                """;
        return jdbcClient.sql(sql)
                .param("familyId", familyId)
                .param("code", code)
                .param("name", name)
                .param("normalizedName", normalizedName)
                .param("sortOrder", sortOrder)
                .query(modelRowMapper)
                .single();
    }


    public List<ManualPdfFamily> findAllFamilies() {
        String sql = """
                select id, code, name, sort_order
                from manual_pdf_family
                where enabled = true
                order by sort_order, name
                """;
        return jdbcClient.sql(sql).query(familyRowMapper).list();
    }

    public List<ManualPdfModel> findModelsByFamilyId(Long familyId) {
        String sql = """
                select id, family_id, code, name, sort_order
                from manual_pdf_model
                where enabled = true
                  and family_id = :familyId
                order by sort_order, name
                """;
        return jdbcClient.sql(sql)
                .param("familyId", familyId)
                .query(modelRowMapper)
                .list();
    }

    public List<Integer> findAvailableYears() {
        String sql = """
                select distinct gs.year_value
                from manual_pdf_document d
                cross join lateral generate_series(d.year_from, d.year_to) as gs(year_value)
                where d.enabled = true
                order by gs.year_value desc
                """;
        return jdbcClient.sql(sql).query(Integer.class).list();
    }

    public List<ManualPdfFamily> findFamiliesByYear(int year) {
        String sql = """
                select x.id, x.code, x.name, x.sort_order
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
        return jdbcClient.sql(sql).param("year", year).query(familyRowMapper).list();
    }

    public List<ManualPdfModel> findModelsByYearAndFamily(int year, Long familyId) {
        String sql = """
                select x.id, x.family_id, x.code, x.name, x.sort_order
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
                .query(modelRowMapper)
                .list();
    }

    public List<ManualPdfDocument> findDocumentsByYearAndFamily(int year, Long familyId) {
        String sql = """
                select d.id, d.model_id, d.title, d.year_from, d.year_to, d.file_name, d.file_key, d.mime_type, d.file_size, d.enabled
                from manual_pdf_document d
                join manual_pdf_model m on m.id = d.model_id
                where d.enabled = true
                  and m.enabled = true
                  and m.family_id = :familyId
                  and :year between d.year_from and d.year_to
                order by m.sort_order, m.name, d.title, d.year_from desc, d.id desc
                """;
        return jdbcClient.sql(sql)
                .param("year", year)
                .param("familyId", familyId)
                .query(documentRowMapper)
                .list();
    }

    public List<ManualPdfDocument> findDocumentsByFamilyId(Long familyId) {
        String sql = """
                select d.id, d.model_id, d.title, d.year_from, d.year_to, d.file_name, d.file_key, d.mime_type, d.file_size, d.enabled
                from manual_pdf_document d
                join manual_pdf_model m on m.id = d.model_id
                join manual_pdf_family f on f.id = m.family_id
                where d.enabled = true
                  and m.enabled = true
                  and f.enabled = true
                  and m.family_id = :familyId
                order by m.sort_order, m.name, d.title, d.year_from desc, d.year_to desc, d.id desc
                """;
        return jdbcClient.sql(sql)
                .param("familyId", familyId)
                .query(documentRowMapper)
                .list();
    }

    public List<ManualPdfDocument> findDocumentsByModelId(Long modelId) {
        String sql = """
                select id, model_id, title, year_from, year_to, file_name, file_key, mime_type, file_size, enabled
                from manual_pdf_document
                where enabled = true
                  and model_id = :modelId
                order by title, year_from desc, year_to desc, id desc
                """;
        return jdbcClient.sql(sql)
                .param("modelId", modelId)
                .query(documentRowMapper)
                .list();
    }

    public Optional<ManualPdfDocument> findBestDocumentByYearAndModel(int year, Long modelId) {
        String sql = """
                select id, model_id, title, year_from, year_to, file_name, file_key, mime_type, file_size, enabled
                from manual_pdf_document
                where enabled = true
                  and model_id = :modelId
                  and :year between year_from and year_to
                order by
                    case when year_from = :year and year_to = :year then 0 else 1 end,
                    (year_to - year_from) asc,
                    updated_at desc,
                    id desc
                limit 1
                """;
        return jdbcClient.sql(sql)
                .param("year", year)
                .param("modelId", modelId)
                .query(documentRowMapper)
                .optional();
    }

    public Optional<ManualPdfDocument> findDocumentById(Long id) {
        String sql = """
                select id, model_id, title, year_from, year_to, file_name, file_key, mime_type, file_size, enabled
                from manual_pdf_document
                where id = :id
                  and enabled = true
                """;
        return jdbcClient.sql(sql).param("id", id).query(documentRowMapper).optional();
    }

    public ManualPdfModelSummary getModelSummary(Long modelId) {
        String sql = """
                select
                    m.id as model_id,
                    m.code as model_code,
                    m.name as model_name,
                    f.id as family_id,
                    f.code as family_code,
                    f.name as family_name
                from manual_pdf_model m
                join manual_pdf_family f on f.id = m.family_id
                where m.id = :modelId
                  and m.enabled = true
                  and f.enabled = true
                """;
        return jdbcClient.sql(sql)
                .param("modelId", modelId)
                .query(summaryRowMapper)
                .optional()
                .orElseThrow(() -> new ManualPdfNotFoundException("No se encontró el modelo seleccionado."));
    }

    public boolean existsDocumentByModelRangeAndTitle(Long modelId, Integer yearFrom, Integer yearTo, String title) {
        String sql = """
                select count(*)
                from manual_pdf_document
                where enabled = true
                  and model_id = :modelId
                  and year_from = :yearFrom
                  and year_to = :yearTo
                  and lower(trim(title)) = lower(trim(:title))
                """;
        Integer count = jdbcClient.sql(sql)
                .param("modelId", modelId)
                .param("yearFrom", yearFrom)
                .param("yearTo", yearTo)
                .param("title", title)
                .query(Integer.class)
                .single();
        return count != null && count > 0;
    }

    public boolean existsAnotherDocumentByModelRangeAndTitle(
            Long documentId,
            Long modelId,
            Integer yearFrom,
            Integer yearTo,
            String title
    ) {
        String sql = """
                select count(*)
                from manual_pdf_document
                where enabled = true
                  and id <> :documentId
                  and model_id = :modelId
                  and year_from = :yearFrom
                  and year_to = :yearTo
                  and lower(trim(title)) = lower(trim(:title))
                """;
        Integer count = jdbcClient.sql(sql)
                .param("documentId", documentId)
                .param("modelId", modelId)
                .param("yearFrom", yearFrom)
                .param("yearTo", yearTo)
                .param("title", title)
                .query(Integer.class)
                .single();
        return count != null && count > 0;
    }

    public Long insertDocumentPlaceholder(Long modelId, String title, Integer yearFrom, Integer yearTo) {
        String sql = """
                insert into manual_pdf_document (
                    model_id, title, year_from, year_to, file_name, file_key, mime_type, file_size
                )
                values (
                    :modelId, :title, :yearFrom, :yearTo, '__PENDING__.pdf', '__PENDING__', 'application/pdf', 0
                )
                returning id
                """;
        return jdbcClient.sql(sql)
                .param("modelId", modelId)
                .param("title", title)
                .param("yearFrom", yearFrom)
                .param("yearTo", yearTo)
                .query(Long.class)
                .single();
    }

    public void updateDocumentStoredFile(Long documentId, String fileName, String fileKey, String mimeType, Long fileSize) {
        String sql = """
                update manual_pdf_document
                set file_name = :fileName,
                    file_key = :fileKey,
                    mime_type = :mimeType,
                    file_size = :fileSize,
                    updated_at = now()
                where id = :documentId
                """;
        jdbcClient.sql(sql)
                .param("documentId", documentId)
                .param("fileName", fileName)
                .param("fileKey", fileKey)
                .param("mimeType", mimeType)
                .param("fileSize", fileSize)
                .update();
    }

    public void updateDocumentMetadata(Long documentId, Long modelId, String title, Integer yearFrom, Integer yearTo) {
        String sql = """
                update manual_pdf_document
                set model_id = :modelId,
                    title = :title,
                    year_from = :yearFrom,
                    year_to = :yearTo,
                    updated_at = now()
                where id = :documentId
                """;
        jdbcClient.sql(sql)
                .param("documentId", documentId)
                .param("modelId", modelId)
                .param("title", title)
                .param("yearFrom", yearFrom)
                .param("yearTo", yearTo)
                .update();
    }

    public List<ManualPdfImage> findImagesByDocumentId(Long documentId) {
        String sql = """
                select id, document_id, file_name, file_key, mime_type, file_size, sort_order, enabled
                from manual_pdf_image
                where document_id = :documentId
                  and enabled = true
                order by sort_order, id
                """;
        return jdbcClient.sql(sql).param("documentId", documentId).query(imageRowMapper).list();
    }

    public Optional<ManualPdfImage> findImageById(Long imageId) {
        String sql = """
                select id, document_id, file_name, file_key, mime_type, file_size, sort_order, enabled
                from manual_pdf_image
                where id = :imageId
                  and enabled = true
                """;
        return jdbcClient.sql(sql).param("imageId", imageId).query(imageRowMapper).optional();
    }

    public int findNextImageSortOrder(Long documentId) {
        String sql = """
                select coalesce(max(sort_order), 0) + 1
                from manual_pdf_image
                where document_id = :documentId
                  and enabled = true
                """;
        Integer next = jdbcClient.sql(sql).param("documentId", documentId).query(Integer.class).single();
        return next == null ? 1 : next;
    }

    public ManualPdfImage insertImage(Long documentId, String fileName, String fileKey, String mimeType, Long fileSize, Integer sortOrder) {
        String sql = """
                insert into manual_pdf_image (document_id, file_name, file_key, mime_type, file_size, sort_order)
                values (:documentId, :fileName, :fileKey, :mimeType, :fileSize, :sortOrder)
                returning id, document_id, file_name, file_key, mime_type, file_size, sort_order, enabled
                """;
        return jdbcClient.sql(sql)
                .param("documentId", documentId)
                .param("fileName", fileName)
                .param("fileKey", fileKey)
                .param("mimeType", mimeType)
                .param("fileSize", fileSize)
                .param("sortOrder", sortOrder)
                .query(imageRowMapper)
                .single();
    }

    public List<ManualPdfImage> findImagesByIds(Long documentId, List<Long> imageIds) {
        if (imageIds == null || imageIds.isEmpty()) {
            return Collections.emptyList();
        }
        String sql = """
                select id, document_id, file_name, file_key, mime_type, file_size, sort_order, enabled
                from manual_pdf_image
                where document_id = :documentId
                  and id in (:imageIds)
                  and enabled = true
                order by sort_order, id
                """;
        return jdbcClient.sql(sql)
                .param("documentId", documentId)
                .param("imageIds", imageIds)
                .query(imageRowMapper)
                .list();
    }

    public void deleteImagesByIds(Long documentId, List<Long> imageIds) {
        if (imageIds == null || imageIds.isEmpty()) {
            return;
        }
        String sql = """
                delete from manual_pdf_image
                where document_id = :documentId
                  and id in (:imageIds)
                """;
        jdbcClient.sql(sql)
                .param("documentId", documentId)
                .param("imageIds", imageIds)
                .update();
    }

    public void updateImageFileKey(Long imageId, String fileKey) {
        String sql = """
                update manual_pdf_image
                set file_key = :fileKey
                where id = :imageId
                """;
        jdbcClient.sql(sql).param("imageId", imageId).param("fileKey", fileKey).update();
    }
}