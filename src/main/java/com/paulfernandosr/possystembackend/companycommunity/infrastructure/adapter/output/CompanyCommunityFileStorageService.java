package com.paulfernandosr.possystembackend.companycommunity.infrastructure.adapter.output;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.ffmpeg.ffmpeg;
import org.bytedeco.javacpp.Loader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyCommunityFileStorageService {

    private static final long MAX_IMAGE_BYTES = 8L * 1024L * 1024L;
    private static final long MAX_VIDEO_BYTES = 200L * 1024L * 1024L;
    private static final long MAX_DOCUMENT_BYTES = 25L * 1024L * 1024L;
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    private static final Set<String> VIDEO_TYPES = Set.of(
            "video/mp4",
            "video/webm",
            "video/quicktime",
            "video/x-m4v",
            "video/3gpp",
            "video/3gpp2"
    );
    private static final Set<String> DOCUMENT_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain",
            "text/csv"
    );

    @Value("${app.files.company-community-dir:./storage/company-community}")
    private String companyCommunityDir;

    @Value("${app.media.transcode-videos:true}")
    private boolean transcodeVideos;

    @Value("${app.media.ffmpeg-path:}")
    private String ffmpegPath;

    private volatile String resolvedFfmpeg;

    public StoredCompanyCommunityFile storePostFile(Long postId, MultipartFile file) {
        return store("posts/" + postId, file);
    }

    public void deleteByKey(String key) {
        if (key == null || key.isBlank()) return;

        try {
            Path baseDir = Paths.get(companyCommunityDir).toAbsolutePath().normalize();
            Path filePath = baseDir.resolve(key).normalize();
            if (!filePath.startsWith(baseDir)) {
                throw new SecurityException("Ruta de archivo invalida.");
            }
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo eliminar el archivo empresarial.", e);
        }
    }

    private StoredCompanyCommunityFile store(String folder, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("El archivo es obligatorio.");
        }

        String originalName = safeOriginalName(file.getOriginalFilename());
        String contentType = contentType(file.getContentType(), originalName);
        String mediaType = mediaType(contentType, originalName);
        validateFile(file, contentType, mediaType);

        try {
            Path baseDir = Paths.get(companyCommunityDir).toAbsolutePath().normalize();
            Path targetDir = baseDir.resolve(folder).normalize();
            Files.createDirectories(targetDir);

            if ("VIDEO".equals(mediaType) && transcodeVideos) {
                StoredCompanyCommunityFile transcoded = storeTranscodedVideo(folder, targetDir, file, originalName);
                if (transcoded != null) return transcoded;
                log.warn("No se pudo transcodificar '{}'; se guardara el video original.", originalName);
            }

            String extension = extension(file.getOriginalFilename(), contentType);
            String filename = UUID.randomUUID() + extension;
            Path destination = targetDir.resolve(filename).normalize();

            if (!destination.startsWith(targetDir)) {
                throw new SecurityException("Ruta de archivo invalida.");
            }

            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);

            return new StoredCompanyCommunityFile(
                    folder + "/" + filename,
                    filename,
                    originalName,
                    contentType,
                    mediaType,
                    file.getSize()
            );
        } catch (IOException e) {
            throw new RuntimeException("No se pudo almacenar el archivo empresarial.", e);
        }
    }

    private StoredCompanyCommunityFile storeTranscodedVideo(String folder, Path targetDir, MultipartFile file, String originalName) throws IOException {
        Path temp = Files.createTempFile("company-community-video-", videoInputSuffix(originalName));
        try {
            Files.copy(file.getInputStream(), temp, StandardCopyOption.REPLACE_EXISTING);

            String filename = UUID.randomUUID() + ".mp4";
            Path destination = targetDir.resolve(filename).normalize();
            if (!destination.startsWith(targetDir)) {
                throw new SecurityException("Ruta de archivo invalida.");
            }

            if (!transcodeToMp4(temp, destination)) {
                return null;
            }

            return new StoredCompanyCommunityFile(
                    folder + "/" + filename,
                    filename,
                    mp4DisplayName(originalName),
                    "video/mp4",
                    "VIDEO",
                    Files.size(destination)
            );
        } finally {
            safeDelete(temp);
        }
    }

    private String resolveFfmpeg() {
        if (ffmpegPath != null && !ffmpegPath.isBlank()) return ffmpegPath;
        if (resolvedFfmpeg == null) {
            synchronized (this) {
                if (resolvedFfmpeg == null) resolvedFfmpeg = Loader.load(ffmpeg.class);
            }
        }
        return resolvedFfmpeg;
    }

    private boolean transcodeToMp4(Path input, Path output) {
        String ffmpegExecutable;
        try {
            ffmpegExecutable = resolveFfmpeg();
        } catch (Throwable throwable) {
            log.warn("No se pudo obtener ffmpeg: {}", throwable.getMessage());
            safeDelete(output);
            return false;
        }

        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(
                    ffmpegExecutable,
                    "-y",
                    "-i", input.toString(),
                    "-c:v", "libopenh264",
                    "-b:v", "3500k",
                    "-pix_fmt", "yuv420p",
                    "-c:a", "aac",
                    "-b:a", "128k",
                    "-movflags", "+faststart",
                    output.toString()
            );
            builder.redirectErrorStream(true);
            process = builder.start();
            try (InputStream stream = process.getInputStream()) {
                stream.readAllBytes();
            }
            boolean finished = process.waitFor(240, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                safeDelete(output);
                return false;
            }
            if (process.exitValue() != 0) {
                safeDelete(output);
                return false;
            }
            return Files.exists(output) && Files.size(output) > 0;
        } catch (IOException exception) {
            log.warn("No se pudo ejecutar ffmpeg ('{}'): {}", ffmpegExecutable, exception.getMessage());
            safeDelete(output);
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (process != null) process.destroyForcibly();
            safeDelete(output);
            return false;
        }
    }

    private static void validateFile(MultipartFile file, String contentType, String mediaType) {
        switch (mediaType) {
            case "IMAGE" -> {
                if (file.getSize() > MAX_IMAGE_BYTES) throw new IllegalArgumentException("Cada imagen debe pesar maximo 8 MB.");
                if (!IMAGE_TYPES.contains(contentType)) throw new IllegalArgumentException("Formato de imagen no permitido.");
            }
            case "VIDEO" -> {
                if (file.getSize() > MAX_VIDEO_BYTES) throw new IllegalArgumentException("El video excede 200 MB.");
                if (!VIDEO_TYPES.contains(contentType)) throw new IllegalArgumentException("Formato de video no permitido.");
            }
            case "DOCUMENT" -> {
                if (file.getSize() > MAX_DOCUMENT_BYTES) throw new IllegalArgumentException("El documento excede 25 MB.");
            }
            default -> throw new IllegalArgumentException("Solo se permiten imagenes, videos y documentos.");
        }
    }

    private static String mediaType(String contentType, String originalName) {
        if (contentType.startsWith("image/")) return "IMAGE";
        if (contentType.startsWith("video/")) return "VIDEO";
        String name = originalName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".mov") || name.endsWith(".m4v") || name.endsWith(".3gp") || name.endsWith(".3gpp")) return "VIDEO";
        if (DOCUMENT_TYPES.contains(contentType)
                || name.endsWith(".pdf") || name.endsWith(".doc") || name.endsWith(".docx")
                || name.endsWith(".xls") || name.endsWith(".xlsx")
                || name.endsWith(".ppt") || name.endsWith(".pptx")
                || name.endsWith(".txt") || name.endsWith(".csv")) return "DOCUMENT";
        return "FILE";
    }

    private static String contentType(String value, String originalName) {
        String clean = String.valueOf(value == null ? "" : value).trim().toLowerCase(Locale.ROOT);
        if (!clean.isBlank() && !"application/octet-stream".equals(clean)) return clean;

        String name = originalName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".webp")) return "image/webp";
        if (name.endsWith(".gif")) return "image/gif";
        if (name.endsWith(".mp4")) return "video/mp4";
        if (name.endsWith(".webm")) return "video/webm";
        if (name.endsWith(".mov")) return "video/quicktime";
        if (name.endsWith(".m4v")) return "video/x-m4v";
        if (name.endsWith(".3gp")) return "video/3gpp";
        if (name.endsWith(".3gpp")) return "video/3gpp2";
        if (name.endsWith(".pdf")) return "application/pdf";
        if (name.endsWith(".doc")) return "application/msword";
        if (name.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (name.endsWith(".xls")) return "application/vnd.ms-excel";
        if (name.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (name.endsWith(".ppt")) return "application/vnd.ms-powerpoint";
        if (name.endsWith(".pptx")) return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        if (name.endsWith(".txt")) return "text/plain";
        if (name.endsWith(".csv")) return "text/csv";
        return clean;
    }

    private static String extension(String originalFilename, String contentType) {
        String name = safeOriginalName(originalFilename);
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) return name.substring(dot).toLowerCase(Locale.ROOT);
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            case "video/webm" -> ".webm";
            case "video/quicktime" -> ".mov";
            case "video/x-m4v" -> ".m4v";
            case "video/3gpp" -> ".3gp";
            case "video/3gpp2" -> ".3gpp";
            case "application/pdf" -> ".pdf";
            default -> ".mp4";
        };
    }

    private static String safeOriginalName(String value) {
        String clean = String.valueOf(value == null ? "archivo" : value).trim();
        if (clean.isEmpty()) return "archivo";
        return clean.replaceAll("[\\\\/\\r\\n\\t]", "_");
    }

    private static String videoInputSuffix(String originalName) {
        String name = originalName.toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot >= 0 && dot < name.length() - 1 ? name.substring(dot) : ".mov";
    }

    private static String mp4DisplayName(String originalName) {
        int dot = originalName.lastIndexOf('.');
        String base = dot > 0 ? originalName.substring(0, dot) : originalName;
        return base + ".mp4";
    }

    private static void safeDelete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // sin acciones
        }
    }

    public record StoredCompanyCommunityFile(
            String key,
            String fileName,
            String originalName,
            String contentType,
            String mediaType,
            Long sizeBytes
    ) {
    }
}
