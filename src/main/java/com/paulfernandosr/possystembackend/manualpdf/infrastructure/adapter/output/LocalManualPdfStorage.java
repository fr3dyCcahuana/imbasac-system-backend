package com.paulfernandosr.possystembackend.manualpdf.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfFile;
import com.paulfernandosr.possystembackend.manualpdf.domain.StoredFileResult;
import com.paulfernandosr.possystembackend.manualpdf.domain.exception.ManualPdfFileNotFoundException;
import com.paulfernandosr.possystembackend.manualpdf.infrastructure.config.ManualPdfProperties;
import com.paulfernandosr.possystembackend.manualpdf.infrastructure.util.ManualPdfTextUtils;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
@RequiredArgsConstructor
public class LocalManualPdfStorage {

    private final ManualPdfProperties properties;

    public StoredFileResult save(MultipartFile multipartFile, String relativeDir, String preferredFileName) {
        try {
            Path baseDir = getBaseDir();
            Path targetDir = baseDir.resolve(relativeDir).normalize();
            ensureInsideBase(baseDir, targetDir);
            Files.createDirectories(targetDir);

            String sanitizedName = ManualPdfTextUtils.sanitizeFileName(preferredFileName, "file");
            String finalName = resolveUniqueFileName(targetDir, sanitizedName);
            Path targetFile = targetDir.resolve(finalName).normalize();
            ensureInsideBase(baseDir, targetFile);

            multipartFile.transferTo(targetFile);

            String mimeType = multipartFile.getContentType() != null
                    ? multipartFile.getContentType()
                    : Files.probeContentType(targetFile);

            return new StoredFileResult(
                    finalName,
                    (relativeDir + "/" + finalName).replace("\\", "/"),
                    mimeType != null ? mimeType : "application/octet-stream",
                    Files.size(targetFile)
            );
        } catch (IOException e) {
            throw new RuntimeException("No se pudo guardar el archivo físico.", e);
        }
    }

    public ManualPdfFile load(String fileKey, String mimeType) {
        try {
            Path path = resolveFilePath(fileKey);
            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                throw new ManualPdfFileNotFoundException("No se encontró el archivo físico.");
            }
            Resource resource = new FileSystemResource(path);
            String resolvedMime = mimeType != null ? mimeType : Files.probeContentType(path);
            return new ManualPdfFile(
                    path.getFileName().toString(),
                    resolvedMime != null ? resolvedMime : "application/octet-stream",
                    Files.size(path),
                    resource
            );
        } catch (IOException e) {
            throw new RuntimeException("No se pudo cargar el archivo físico.", e);
        }
    }

    public void delete(String fileKey) {
        try {
            Path path = resolveFilePath(fileKey);
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo eliminar el archivo físico.", e);
        }
    }

    public void move(String oldFileKey, String newFileKey) {
        try {
            if (oldFileKey.equals(newFileKey)) {
                return;
            }
            Path oldPath = resolveFilePath(oldFileKey);
            if (!Files.exists(oldPath) || !Files.isRegularFile(oldPath)) {
                throw new ManualPdfFileNotFoundException("No se encontró el archivo físico a mover.");
            }
            Path newPath = resolveFilePath(newFileKey);
            if (newPath.getParent() != null) {
                Files.createDirectories(newPath.getParent());
            }
            if (Files.exists(newPath)) {
                throw new RuntimeException("Ya existe un archivo físico en la ruta destino.");
            }
            Files.move(oldPath, newPath);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo mover el archivo físico.", e);
        }
    }

    private Path getBaseDir() {
        return Paths.get(properties.getManualPdfsDir()).toAbsolutePath().normalize();
    }

    private Path resolveFilePath(String fileKey) {
        Path baseDir = getBaseDir();
        Path path = baseDir.resolve(fileKey).normalize();
        ensureInsideBase(baseDir, path);
        return path;
    }

    private void ensureInsideBase(Path baseDir, Path path) {
        if (!path.startsWith(baseDir)) {
            throw new SecurityException("Ruta de archivo inválida.");
        }
    }

    private String resolveUniqueFileName(Path dir, String fileName) {
        Path candidate = dir.resolve(fileName);
        if (!Files.exists(candidate)) {
            return fileName;
        }
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String ext = dot > 0 ? fileName.substring(dot) : "";
        int counter = 1;
        while (true) {
            String candidateName = base + "_" + counter + ext;
            if (!Files.exists(dir.resolve(candidateName))) {
                return candidateName;
            }
            counter++;
        }
    }
}