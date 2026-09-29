package com.unicompanion.learning.material.infrastructure;

import com.unicompanion.learning.config.StorageProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Component
public class LocalPdfStorage {

    private final Path root;

    public LocalPdfStorage(StorageProperties properties) {
        this.root = Path.of(properties.path());
    }

    public String store(UUID materialId, String fileName, byte[] bytes) {
        Path directory = root.resolve(materialId.toString());
        try {
            Files.createDirectories(directory);
            Files.write(directory.resolve(fileName), bytes);
            return materialId + "/" + fileName;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public byte[] read(String relativePath) throws IOException {
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root.normalize())) {
            throw new IOException("Storage path escapes the material directory");
        }
        return Files.readAllBytes(resolved);
    }

    public void delete(UUID materialId) {
        Path directory = root.resolve(materialId.toString()).normalize();
        if (!directory.startsWith(root.normalize()) || !Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            });
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
