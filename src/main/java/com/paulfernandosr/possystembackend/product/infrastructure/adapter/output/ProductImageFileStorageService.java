package com.paulfernandosr.possystembackend.product.infrastructure.adapter.output;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductImageFileStorageService {

    @Value("${app.files.products-images-dir}")
    private String productsImagesDir;

    public String store(Long productId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("El archivo de imagen es obligatorio.");
        }

        try {
            Path baseDir = Paths.get(productsImagesDir).toAbsolutePath().normalize();
            Path productDir = baseDir.resolve(String.valueOf(productId)).normalize();
            Files.createDirectories(productDir);

            String originalFilename = file.getOriginalFilename();
            String extension = "";

            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }

            String filename = UUID.randomUUID() + extension;
            Path destinationFile = productDir.resolve(filename).normalize();

            // Protección path traversal
            if (!destinationFile.startsWith(productDir)) {
                throw new SecurityException("Ruta de archivo inválida.");
            }

            Files.copy(file.getInputStream(), destinationFile, StandardCopyOption.REPLACE_EXISTING);

            // ✅ SOLO guardamos KEY en BD
            return productId + "/" + filename;

        } catch (IOException e) {
            throw new RuntimeException("No se pudo almacenar el archivo de imagen", e);
        }
    }

    public void deleteByKey(String imageKey) {
        if (imageKey == null || imageKey.isBlank()) return;

        try {
            Path baseDir = Paths.get(productsImagesDir).toAbsolutePath().normalize();
            Path filePath = baseDir.resolve(imageKey).normalize();

            if (!filePath.startsWith(baseDir)) {
                throw new SecurityException("Ruta de archivo inválida.");
            }

            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            // Decide si aquí quieres loggear y seguir, o lanzar error
            throw new RuntimeException("No se pudo eliminar la imagen física", e);
        }
    }
}