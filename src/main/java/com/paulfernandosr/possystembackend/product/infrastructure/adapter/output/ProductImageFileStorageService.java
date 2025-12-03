package com.paulfernandosr.possystembackend.product.infrastructure.adapter.output;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductImageFileStorageService {

    @Value("${app.files.products-images-dir}")
    private String productsImagesDir;       // Ej: /var/www/altoquik/images/products

    @Value("${app.files.products-images-base-url}")
    private String productsImagesBaseUrl;   // Ej: https://cdn.altoquik.com/images/products

    public String store(Long productId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("El archivo de imagen es obligatorio.");
        }

        try {
            // Directorio del producto: /.../products/{productId}
            Path productDir = Paths.get(productsImagesDir, String.valueOf(productId));
            Files.createDirectories(productDir);

            // Nombre de archivo seguro: UUID + extensión original
            String originalFilename = file.getOriginalFilename();
            String extension = "";

            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }

            String filename = UUID.randomUUID() + extension;
            Path destinationFile = productDir.resolve(filename).normalize();

            // Guardar físicamente el archivo
            Files.copy(file.getInputStream(), destinationFile, StandardCopyOption.REPLACE_EXISTING);

            // Construir URL pública: {baseUrl}/{productId}/{filename}
            String relativePath = productId + "/" + filename;
            return productsImagesBaseUrl.replaceAll("/$", "") + "/" + relativePath;

        } catch (IOException e) {
            throw new RuntimeException("No se pudo almacenar el archivo de imagen", e);
        }
    }
}
