package com.paulfernandosr.possystembackend.community.infrastructure.adapter.output;

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
public class CommunityFileStorageService {

    private static final long MAX_IMAGE_BYTES = 5L * 1024L * 1024L;
    private static final long MAX_VIDEO_BYTES = 100L * 1024L * 1024L;
    private static final long MAX_DOCUMENT_BYTES = 25L * 1024L * 1024L;
    private static final long MAX_ARCHIVE_BYTES = 50L * 1024L * 1024L;
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
    private static final Set<String> ARCHIVE_TYPES = Set.of(
            "application/zip",
            "application/x-zip-compressed",
            "application/x-rar-compressed",
            "application/vnd.rar",
            "application/x-7z-compressed"
    );

    @Value("${app.files.community-dir:./storage/community}")
    private String communityDir;

    // Transcodificación de videos a H.264/MP4 (compatibilidad con todos los navegadores: iPhone HEVC/.mov, Android, etc.)
    @Value("${app.media.transcode-videos:true}")
    private boolean transcodeVideos;

    // Vacío por defecto: usa el ffmpeg empaquetado (Bytedeco). Solo se establece para forzar un binario del sistema.
    @Value("${app.media.ffmpeg-path:}")
    private String ffmpegPath;

    private volatile String resolvedFfmpeg;

    public StoredCommunityFile storePostFile(Long postId, MultipartFile file) {
        return store("posts/" + postId, file);
    }

    public StoredCommunityFile storeCommentFile(Long postId, Long commentId, MultipartFile file) {
        return store("posts/" + postId + "/comments/" + commentId, file);
    }

    public void deleteByKey(String key) {
        if (key == null || key.isBlank()) return;

        try {
            Path baseDir = Paths.get(communityDir).toAbsolutePath().normalize();
            Path filePath = baseDir.resolve(key).normalize();
            if (!filePath.startsWith(baseDir)) {
                throw new SecurityException("Ruta de archivo invalida.");
            }
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo eliminar el archivo de comunidad.", e);
        }
    }

    private StoredCommunityFile store(String folder, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("El archivo es obligatorio.");
        }

        String originalName = safeOriginalName(file.getOriginalFilename());
        String contentType = contentType(file.getContentType(), originalName);
        String mediaType = mediaType(contentType, originalName);
        validateFile(file, contentType, mediaType);

        try {
            Path baseDir = Paths.get(communityDir).toAbsolutePath().normalize();
            Path targetDir = baseDir.resolve(folder).normalize();
            Files.createDirectories(targetDir);

            // Los videos se transcodifican a H.264/MP4 para que se reproduzcan en cualquier navegador
            // (el iPhone graba HEVC/.mov y algunos Android también, formatos que Chrome/Firefox no decodifican).
            if ("VIDEO".equals(mediaType) && transcodeVideos) {
                StoredCommunityFile transcoded = storeTranscodedVideo(folder, targetDir, file, originalName);
                if (transcoded != null) {
                    return transcoded;
                }
                log.warn("Transcodificacion no disponible (ffmpeg); se guarda el video original '{}' sin convertir.", originalName);
            }

            String extension = extension(file.getOriginalFilename(), contentType);
            String filename = UUID.randomUUID() + extension;
            Path destination = targetDir.resolve(filename).normalize();

            if (!destination.startsWith(targetDir)) {
                throw new SecurityException("Ruta de archivo invalida.");
            }

            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);

            return new StoredCommunityFile(
                    folder + "/" + filename,
                    filename,
                    originalName,
                    contentType,
                    mediaType,
                    file.getSize()
            );
        } catch (IOException e) {
            throw new RuntimeException("No se pudo almacenar el archivo de comunidad.", e);
        }
    }

    private StoredCommunityFile storeTranscodedVideo(String folder, Path targetDir, MultipartFile file, String originalName) throws IOException {
        Path temp = Files.createTempFile("community-video-", videoInputSuffix(originalName));
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

            long size = Files.size(destination);
            return new StoredCommunityFile(
                    folder + "/" + filename,
                    filename,
                    mp4DisplayName(originalName),
                    "video/mp4",
                    "VIDEO",
                    size
            );
        } finally {
            safeDelete(temp);
        }
    }

    /**
     * Resuelve la ruta del ejecutable ffmpeg: si se configuró {@code app.media.ffmpeg-path} usa ese binario del
     * sistema; en caso contrario extrae y usa el binario empaquetado por Bytedeco (no requiere instalación).
     */
    private String resolveFfmpeg() {
        if (ffmpegPath != null && !ffmpegPath.isBlank()) {
            return ffmpegPath;
        }
        if (resolvedFfmpeg == null) {
            synchronized (this) {
                if (resolvedFfmpeg == null) {
                    resolvedFfmpeg = Loader.load(ffmpeg.class);
                }
            }
        }
        return resolvedFfmpeg;
    }

    private boolean transcodeToMp4(Path input, Path output) {
        String ffmpegExecutable;
        try {
            ffmpegExecutable = resolveFfmpeg();
        } catch (Throwable throwable) {
            log.warn("No se pudo obtener el binario de ffmpeg: {}", throwable.getMessage());
            safeDelete(output);
            return false;
        }

        Process process = null;
        try {
            // Se usa libopenh264 (no libx264): el ffmpeg empaquetado por Bytedeco es LGPL y no incluye libx264 (GPL).
            // libopenh264 no admite -preset/-crf; el control de calidad es por bitrate (-b:v).
            ProcessBuilder builder = new ProcessBuilder(
                    ffmpegExecutable,
                    "-y",
                    "-i", input.toString(),
                    "-c:v", "libopenh264",
                    "-b:v", "2500k",
                    "-pix_fmt", "yuv420p",
                    "-c:a", "aac",
                    "-b:a", "128k",
                    "-movflags", "+faststart",
                    output.toString()
            );
            builder.redirectErrorStream(true);
            process = builder.start();

            // Drenar la salida para que el proceso no se bloquee si el buffer se llena.
            try (InputStream stream = process.getInputStream()) {
                stream.readAllBytes();
            }

            boolean finished = process.waitFor(180, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("ffmpeg excedio el tiempo de transcodificacion.");
                safeDelete(output);
                return false;
            }
            if (process.exitValue() != 0) {
                log.warn("ffmpeg termino con codigo {} al transcodificar el video.", process.exitValue());
                safeDelete(output);
                return false;
            }
            return Files.exists(output) && Files.size(output) > 0;
        } catch (IOException exception) {
            // ffmpeg no instalado o no accesible en la ruta configurada.
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

    private void safeDelete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // sin acciones
        }
    }

    private static String videoInputSuffix(String originalName) {
        String name = originalName.toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            return name.substring(dot);
        }
        return ".mov";
    }

    private static String mp4DisplayName(String originalName) {
        int dot = originalName.lastIndexOf('.');
        String base = dot > 0 ? originalName.substring(0, dot) : originalName;
        return base + ".mp4";
    }

    private static void validateFile(MultipartFile file, String contentType, String mediaType) {
        switch (mediaType) {
            case "IMAGE" -> {
                if (file.getSize() > MAX_IMAGE_BYTES) {
                    throw new IllegalArgumentException("Cada imagen debe pesar maximo 5 MB.");
                }
                if (!IMAGE_TYPES.contains(contentType)) {
                    throw new IllegalArgumentException("Formato de imagen no permitido.");
                }
            }
            case "VIDEO" -> {
                if (file.getSize() > MAX_VIDEO_BYTES) {
                    throw new IllegalArgumentException("El video excede el tamano permitido.");
                }
                if (!VIDEO_TYPES.contains(contentType)) {
                    throw new IllegalArgumentException("Formato de video no permitido.");
                }
            }
            case "DOCUMENT" -> {
                if (file.getSize() > MAX_DOCUMENT_BYTES) {
                    throw new IllegalArgumentException("El documento excede 25 MB.");
                }
            }
            case "ARCHIVE" -> {
                if (file.getSize() > MAX_ARCHIVE_BYTES) {
                    throw new IllegalArgumentException("El archivo comprimido excede 50 MB.");
                }
            }
            default -> throw new IllegalArgumentException(
                    "Solo se permiten imagenes, videos, documentos (PDF/DOC/XLS) o comprimidos (ZIP).");
        }
    }

    private static String mediaType(String contentType, String originalName) {
        if (contentType.startsWith("image/")) return "IMAGE";
        if (contentType.startsWith("video/")) return "VIDEO";
        String name = originalName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".mov") || name.endsWith(".m4v") || name.endsWith(".3gp") || name.endsWith(".3gpp")) {
            return "VIDEO";
        }
        if (DOCUMENT_TYPES.contains(contentType)
                || name.endsWith(".pdf") || name.endsWith(".doc") || name.endsWith(".docx")
                || name.endsWith(".xls") || name.endsWith(".xlsx")
                || name.endsWith(".ppt") || name.endsWith(".pptx")
                || name.endsWith(".txt") || name.endsWith(".csv")) {
            return "DOCUMENT";
        }
        if (ARCHIVE_TYPES.contains(contentType)
                || name.endsWith(".zip") || name.endsWith(".rar") || name.endsWith(".7z")) {
            return "ARCHIVE";
        }
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
        if (name.endsWith(".zip")) return "application/zip";
        if (name.endsWith(".rar")) return "application/vnd.rar";
        if (name.endsWith(".7z")) return "application/x-7z-compressed";
        return clean;
    }

    private static String extension(String originalFilename, String contentType) {
        String name = safeOriginalName(originalFilename);
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            return name.substring(dot).toLowerCase(Locale.ROOT);
        }
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
            default -> ".mp4";
        };
    }

    private static String safeOriginalName(String value) {
        String clean = String.valueOf(value == null ? "archivo" : value).trim();
        if (clean.isEmpty()) return "archivo";
        return clean.replaceAll("[\\\\/\\r\\n\\t]", "_");
    }

    public record StoredCommunityFile(
            String key,
            String fileName,
            String originalName,
            String contentType,
            String mediaType,
            Long sizeBytes
    ) {
    }
}
