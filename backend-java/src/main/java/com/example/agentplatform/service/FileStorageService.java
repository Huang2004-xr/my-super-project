package com.example.agentplatform.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileStorageService {
    private final Path rootDir;

    public FileStorageService(@Value("${upload.storage-root-dir}") String rootDir) {
        this.rootDir = Paths.get(rootDir).toAbsolutePath().normalize();
    }

    public String store(MultipartFile file, String userId, String assetType, String fileAssetId) throws IOException {
        String extension = extension(file.getOriginalFilename());
        Path targetDir = rootDir.resolve(safeSegment(userId)).resolve(safeSegment(assetType).toLowerCase()).normalize();
        Files.createDirectories(targetDir);
        Path target = targetDir.resolve(fileAssetId + extension).normalize();
        if (!target.startsWith(rootDir)) {
            throw new IOException("invalid storage path");
        }
        try (InputStream input = file.getInputStream()) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target.toString();
    }

    public Path resolveStoredPath(String storagePath) throws IOException {
        if (storagePath == null || storagePath.trim().isEmpty()) {
            throw new IOException("file storage path is empty");
        }
        Path path = Paths.get(storagePath).toAbsolutePath().normalize();
        if (!path.startsWith(rootDir)) {
            throw new IOException("file storage path is outside storage root");
        }
        if (!Files.isRegularFile(path)) {
            throw new IOException("stored file does not exist");
        }
        return path;
    }

    public boolean deleteStoredPath(String storagePath) throws IOException {
        Path path = resolveStoredPath(storagePath);
        return Files.deleteIfExists(path);
    }

    private String extension(String fileName) {
        if (fileName == null) {
            return "";
        }
        String normalized = fileName.replace("\\", "/");
        int slash = normalized.lastIndexOf('/');
        String baseName = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        int dot = baseName.lastIndexOf('.');
        if (dot < 0 || dot == baseName.length() - 1) {
            return "";
        }
        String extension = baseName.substring(dot).toLowerCase();
        return extension.matches("\\.[a-z0-9]{1,12}") ? extension : "";
    }

    private String safeSegment(String value) {
        return value == null ? "unknown" : value.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
