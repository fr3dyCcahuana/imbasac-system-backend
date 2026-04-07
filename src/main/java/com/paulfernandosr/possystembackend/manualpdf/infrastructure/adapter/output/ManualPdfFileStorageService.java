package com.paulfernandosr.possystembackend.manualpdf.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfFile;
import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfStoredFile;
import com.paulfernandosr.possystembackend.manualpdf.domain.exception.InvalidManualPdfException;
import com.paulfernandosr.possystembackend.manualpdf.domain.exception.ManualPdfFileNotFoundException;
import com.paulfernandosr.possystembackend.manualpdf.domain.port.output.ManualPdfFileRepository;
import com.paulfernandosr.possystembackend.manualpdf.infrastructure.config.ManualPdfProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.text.Normalizer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ManualPdfFileStorageService implements ManualPdfFileRepository {

    private final ManualPdfProperties properties;

    @Override
    public ManualPdfStoredFile store(String familyCode, String modelCode, Integer yearFrom, Integer yearTo, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidManualPdfException("El archivo PDF es obligatorio.");
        }

        validatePdf(file);

        try {
            Path baseDir = Paths.get(properties.getManualPdfsDir()).toAbsolutePath().normalize();
            String yearSegment = yearFrom.equals(yearTo) ? String.valueOf(yearFrom) : (yearFrom + "-" + yearTo);

            String safeFamily = sanitizePathSegment(familyCode);
            String safeModel = sanitizePathSegment(modelCode);
            String originalBaseName = sanitizeFileBaseName(getBaseName(file.getOriginalFilename()));
            String storedFileName = UUID.randomUUID() + "-" + originalBaseName + ".pdf";

            Path targetDir = baseDir
                    .resolve(safeFamily)
                    .resolve(safeModel)
                    .resolve(yearSegment)
                    .normalize();

            Files.createDirectories(targetDir);

            Path destinationFile = targetDir.resolve(storedFileName).normalize();

            if (!destinationFile.startsWith(baseDir)) {
                throw new SecurityException("Ruta de archivo inválida.");
            }

            Files.copy(file.getInputStream(), destinationFile, StandardCopyOption.REPLACE_EXISTING);

            String relativeKey = safeFamily + "/" + safeModel + "/" + yearSegment + "/" + storedFileName;

            return new ManualPdfStoredFile(
                    relativeKey,
                    storedFileName,
                    "application/pdf",
                    Files.size(destinationFile)
            );
        } catch (IOException e) {
            throw new RuntimeException("No se pudo almacenar el archivo PDF.", e);
        }
    }

    @Override
    public ManualPdfFile load(String fileKey, String fileName, String mimeType) {
        try {
            Path baseDir = Paths.get(properties.getManualPdfsDir()).toAbsolutePath().normalize();
            Path filePath = baseDir.resolve(fileKey).normalize();

            if (!filePath.startsWith(baseDir)) {
                throw new SecurityException("Ruta de archivo inválida.");
            }

            if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
                throw new ManualPdfFileNotFoundException("No se encontró el archivo PDF físico.");
            }

            Resource resource = new FileSystemResource(filePath);

            return new ManualPdfFile(
                    fileName,
                    mimeType != null && !mimeType.isBlank() ? mimeType : "application/pdf",
                    Files.size(filePath),
                    resource
            );
        } catch (ManualPdfFileNotFoundException e) {
            throw e;
        } catch (IOException e) {
            throw new RuntimeException("No se pudo cargar el archivo PDF.", e);
        }
    }

    @Override
    public void deleteByKey(String fileKey) {
        if (fileKey == null || fileKey.isBlank()) return;

        try {
            Path baseDir = Paths.get(properties.getManualPdfsDir()).toAbsolutePath().normalize();
            Path filePath = baseDir.resolve(fileKey).normalize();

            if (!filePath.startsWith(baseDir)) {
                throw new SecurityException("Ruta de archivo inválida.");
            }

            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo eliminar el archivo PDF físico.", e);
        }
    }

    private void validatePdf(MultipartFile file) {
        String original = file.getOriginalFilename();
        String extension = original != null && original.contains(".")
                ? original.substring(original.lastIndexOf(".")).toLowerCase(Locale.ROOT)
                : "";

        if (!".pdf".equals(extension)) {
            throw new InvalidManualPdfException("El archivo debe tener extensión .pdf.");
        }

        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank() && !"application/pdf".equalsIgnoreCase(contentType)) {
            throw new InvalidManualPdfException("El archivo debe ser un PDF válido.");
        }
    }

    private String sanitizePathSegment(String value) {
        String sanitized = stripAccents(value == null ? "" : value.trim())
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (sanitized.isBlank()) {
            throw new InvalidManualPdfException("No se pudo generar un segmento de ruta válido.");
        }
        return sanitized;
    }

    private String sanitizeFileBaseName(String value) {
        String sanitized = stripAccents(value == null ? "" : value.trim())
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return sanitized.isBlank() ? "manual-pdf" : sanitized;
    }

    private String getBaseName(String filename) {
        if (filename == null || filename.isBlank()) return "manual-pdf";
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(0, lastDot) : filename;
    }

    private String stripAccents(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
    }
}
