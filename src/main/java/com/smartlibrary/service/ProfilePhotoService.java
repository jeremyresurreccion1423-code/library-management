package com.smartlibrary.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ProfilePhotoService {

    private static final long MAX_PHOTO_BYTES = 5L * 1024 * 1024;
    private static final List<String> SUPPORTED_EXTENSIONS = List.of(
            ".jpg", ".jpeg", ".png", ".webp"
    );
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp"
    );
    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            ".exe", ".php", ".jsp", ".bat", ".sh", ".js"
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
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Profile image must be JPG, PNG, or WEBP only.");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        String ext = dot >= 0 ? name.substring(dot) : "";
        if (!SUPPORTED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("Profile image extension must be .jpg, .jpeg, .png, or .webp.");
        }
        if (BLOCKED_EXTENSIONS.contains(ext)
                || name.endsWith(".exe") || name.endsWith(".php") || name.endsWith(".jsp")
                || name.endsWith(".bat") || name.endsWith(".sh") || name.endsWith(".js")) {
            throw new IllegalArgumentException("This file type is not allowed.");
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
            throw new IllegalArgumentException("Profile image extension must be .jpg, .jpeg, .png, or .webp.");
        }
        String ext = fileName.substring(fileName.lastIndexOf('.')).toLowerCase(Locale.ROOT);
        if (!SUPPORTED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("Profile image extension must be .jpg, .jpeg, .png, or .webp.");
        }
        return ext;
    }
}
