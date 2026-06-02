package com.paulfernandosr.possystembackend.promotion.infrastructure.adapter.output;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PromotionImageFileStorageService {

    @Value("${app.files.promotions-images-dir:uploads/promotions}")
    private String promotionsImagesDir;

    public String store(Long campaignId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("El archivo de imagen es obligatorio.");
        }

        try {
            Path baseDir = Paths.get(promotionsImagesDir).toAbsolutePath().normalize();
            Path campaignDir = baseDir.resolve(String.valueOf(campaignId)).normalize();
            Files.createDirectories(campaignDir);

            String originalFilename = file.getOriginalFilename();
            String extension = "";

            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }

            String filename = UUID.randomUUID() + extension;
            Path destinationFile = campaignDir.resolve(filename).normalize();

            // Protección contra path traversal
            if (!destinationFile.startsWith(campaignDir)) {
                throw new SecurityException("Ruta de archivo inválida.");
            }

            Files.copy(file.getInputStream(), destinationFile, StandardCopyOption.REPLACE_EXISTING);

            // Solo guardamos KEY en BD
            return campaignId + "/" + filename;

        } catch (IOException e) {
            throw new RuntimeException("No se pudo almacenar la imagen", e);
        }
    }

    public void deleteByKey(String imageKey) {
        if (imageKey == null || imageKey.isBlank()) return;

        try {
            Path baseDir = Paths.get(promotionsImagesDir).toAbsolutePath().normalize();
            Path filePath = baseDir.resolve(imageKey).normalize();

            if (!filePath.startsWith(baseDir)) {
                throw new SecurityException("Ruta de archivo inválida.");
            }

            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo eliminar la imagen física", e);
        }
    }
}
