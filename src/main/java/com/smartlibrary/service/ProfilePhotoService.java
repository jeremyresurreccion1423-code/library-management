package com.smartlibrary.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

@Service
public class ProfilePhotoService {

    private static final long MAX_PHOTO_BYTES = 5L * 1024 * 1024;
    private static final List<String> SUPPORTED_EXTENSIONS = List.of(
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp"
    );

    public String resolveProfilePhotoUrl(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }

        String safeName = sanitizeUsername(username);
        Path uploadsDir = uploadsDirectory();

        if (!Files.isDirectory(uploadsDir)) {
            return null;
        }

        for (String extension : SUPPORTED_EXTENSIONS) {
            Path candidate = uploadsDir.resolve(safeName + extension);
            if (Files.isRegularFile(candidate)) {
                try {
                    long version = Files.getLastModifiedTime(candidate).toMillis();
                    return "/uploads/" + candidate.getFileName() + "?v=" + version;
                } catch (IOException ignored) {
                    return "/uploads/" + candidate.getFileName();
                }
            }
        }

        return null;
    }

    public void saveProfilePhoto(String username, MultipartFile file) throws IOException {
        validateProfilePhoto(file);
        String safeName = sanitizeUsername(username);
        String extension = extensionFor(file.getOriginalFilename());
        Path uploadDir = uploadsDirectory();
        Files.createDirectories(uploadDir);

        for (String ext : SUPPORTED_EXTENSIONS) {
            Path existing = uploadDir.resolve(safeName + ext);
            Files.deleteIfExists(existing);
        }

        Path target = uploadDir.resolve(safeName + extension);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
    }

    public String getInitial(String username) {
        if (username == null || username.isBlank()) {
            return "U";
        }
        return username.substring(0, 1).toUpperCase();
    }

    public void validateProfilePhoto(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Please choose a profile image.");
        }
        if (file.getSize() > MAX_PHOTO_BYTES) {
            throw new IllegalArgumentException("Profile image must be 5MB or smaller.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("Profile image must be a valid image file.");
        }
    }

    private Path uploadsDirectory() {
        return Paths.get("uploads").toAbsolutePath().normalize();
    }

    private String sanitizeUsername(String username) {
        return username.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String extensionFor(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return ".png";
        }
        String ext = fileName.substring(fileName.lastIndexOf('.')).toLowerCase();
        if (!SUPPORTED_EXTENSIONS.contains(ext)) {
            return ".png";
        }
        return ext;
    }
}
