package com.smartlab.service.impl;

import com.smartlab.dto.response.FileResponse;
import com.smartlab.entity.StoredFileEntity;
import com.smartlab.entity.UserEntity;
import com.smartlab.repo.StoredFileRepository;
import com.smartlab.repo.MemberProfileRepository;
import com.smartlab.repo.UserRepository;
import com.smartlab.service.FileService;
import com.smartlab.service.PostContentFileService;
import com.smartlab.storage.FileStorage;
import com.smartlab.storage.StorageException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService, PostContentFileService {
    private static final String PROVIDER = "GOOGLE_DRIVE";
    // PROJECT is intentionally disabled until project membership checks are implemented in D3.
    private static final Set<String> ACCESS_SCOPES = Set.of("PUBLIC", "PRIVATE", "LAB");
    private static final Map<String, Set<String>> ALLOWED_FILE_TYPES = Map.ofEntries(
            Map.entry("image/jpeg", Set.of("jpg", "jpeg")),
            Map.entry("image/png", Set.of("png")),
            Map.entry("image/gif", Set.of("gif")),
            Map.entry("image/webp", Set.of("webp")),
            Map.entry("application/pdf", Set.of("pdf")),
            Map.entry("text/plain", Set.of("txt")),
            Map.entry("text/csv", Set.of("csv")),
            Map.entry("application/zip", Set.of("zip")),
            Map.entry("application/msword", Set.of("doc")),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", Set.of("docx")),
            Map.entry("application/vnd.ms-excel", Set.of("xls")),
            Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", Set.of("xlsx")),
            Map.entry("application/vnd.ms-powerpoint", Set.of("ppt")),
            Map.entry("application/vnd.openxmlformats-officedocument.presentationml.presentation", Set.of("pptx"))
    );
    private static final byte[] OLE_SIGNATURE = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
            (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};

    private final StoredFileRepository storedFileRepository;
    private final UserRepository userRepository;
    private final FileStorage fileStorage;
    private final MemberProfileRepository memberProfileRepository;

    @Value("${smartlab.file.max-size-bytes:26214400}")
    private long maxFileSizeBytes;

    @Value("${smartlab.storage.provider:google-drive}")
    private String storageProvider;

    @Override
    @Transactional
    public FileResponse upload(MultipartFile file, String accessScope, String description, String email) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File must not be empty");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds the configured size limit");
        }
        String normalizedScope = normalizeScope(accessScope);
        UserEntity owner = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        String originalName = safeFileName(file.getOriginalFilename());
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to read uploaded file", exception);
        }
        String mimeType = normalizeMimeType(file.getContentType());
        validateFileType(originalName, mimeType, content);

        FileStorage.StoredFile stored;
        try {
            if (!"google-drive".equalsIgnoreCase(storageProvider)) {
                throw new StorageException("Unsupported storage provider: " + storageProvider);
            }
            stored = fileStorage.upload(originalName, mimeType, content, description);
        } catch (StorageException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, exception.getMessage(), exception);
        }

        try {
            StoredFileEntity entity = StoredFileEntity.builder()
                    .ownerUser(owner)
                    .storageProvider(PROVIDER)
                    .storageKey(stored.storageKey())
                    .publicUrl(stored.publicUrl())
                    .originalName(originalName)
                    .mimeType(mimeType)
                    .sizeBytes(file.getSize())
                    .accessScope(normalizedScope)
                    .description(description)
                    .build();
            return toResponse(storedFileRepository.save(entity));
        } catch (RuntimeException exception) {
            try {
                fileStorage.trash(stored.storageKey());
            } catch (RuntimeException ignored) {
                // Preserve the database error while keeping the compensation best-effort.
            }
            throw exception;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public DownloadedFile download(Long id, Authentication authentication) {
        StoredFileEntity entity = findActive(id);
        if (!canRead(entity, authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this file");
        }
        try {
            FileStorage.StoredFileContent content = fileStorage.download(entity.getStorageKey());
            return new DownloadedFile(content.content(), entity.getMimeType(), entity.getOriginalName());
        } catch (StorageException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, exception.getMessage(), exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<FileMetadata> findActiveMetadata(Long fileId) {
        return storedFileRepository.findByIdAndDeletedAtIsNull(fileId)
                .map(file -> new FileMetadata(
                        file.getId(),
                        file.getOwnerUser() == null ? null : file.getOwnerUser().getId(),
                        file.getMimeType(),
                        file.getOriginalName(),
                        isAllowedImageMimeType(file.getMimeType())
                ));
    }

    @Override
    @Transactional(readOnly = true)
    public DownloadedContent downloadActiveContent(Long fileId) {
        StoredFileEntity entity = findActive(fileId);
        try {
            FileStorage.StoredFileContent content = fileStorage.download(entity.getStorageKey());
            return new DownloadedContent(content.content(), entity.getMimeType(), entity.getOriginalName());
        } catch (StorageException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, exception.getMessage(), exception);
        }
    }

    @Override
    @Transactional
    public void delete(Long id, String email, Authentication authentication) {
        StoredFileEntity entity = findActive(id);
        boolean owner = entity.getOwnerUser() != null && entity.getOwnerUser().getEmail().equalsIgnoreCase(email);
        if (!owner && !isAdmin(authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only delete your own files");
        }
        if (memberProfileRepository.existsByAvatarFileId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "File is currently used as a member avatar");
        }
        try {
            fileStorage.trash(entity.getStorageKey());
        } catch (StorageException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, exception.getMessage(), exception);
        }
        entity.setDeletedAt(Instant.now());
        storedFileRepository.save(entity);
    }

    private StoredFileEntity findActive(Long id) {
        return storedFileRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));
    }

    private boolean canRead(StoredFileEntity entity, Authentication authentication) {
        if ("PUBLIC".equals(entity.getAccessScope())) {
            return true;
        }
        if (!isAuthenticated(authentication)) {
            return false;
        }
        if (isAdmin(authentication)) {
            return true;
        }
        if ("LAB".equals(entity.getAccessScope())) {
            return true;
        }
        return entity.getOwnerUser() != null
                && entity.getOwnerUser().getEmail().equalsIgnoreCase(authentication.getName());
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getName());
    }

    private String normalizeScope(String accessScope) {
        String normalized = accessScope == null ? "PRIVATE" : accessScope.trim().toUpperCase(Locale.ROOT);
        if (!ACCESS_SCOPES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid access scope; PROJECT files are unavailable until project membership is implemented");
        }
        return normalized;
    }

    private String normalizeMimeType(String contentType) {
        if (contentType == null) return "";
        return contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private void validateFileType(String fileName, String mimeType, byte[] content) {
        Set<String> extensions = ALLOWED_FILE_TYPES.get(mimeType);
        String extension = extensionOf(fileName);
        if (extensions == null || !extensions.contains(extension) || !matchesSignature(mimeType, content)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "File extension, declared type, and content do not match an allowed file type");
        }
    }

    private boolean isAllowedImageMimeType(String mimeType) {
        return mimeType != null && mimeType.startsWith("image/") && ALLOWED_FILE_TYPES.containsKey(mimeType);
    }

    private boolean matchesSignature(String mimeType, byte[] content) {
        return switch (mimeType) {
            case "image/jpeg" -> startsWith(content, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
            case "image/png" -> startsWith(content, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
            case "image/gif" -> startsWith(content, "GIF87a".getBytes(StandardCharsets.US_ASCII))
                    || startsWith(content, "GIF89a".getBytes(StandardCharsets.US_ASCII));
            case "image/webp" -> content.length >= 12
                    && startsWith(content, "RIFF".getBytes(StandardCharsets.US_ASCII))
                    && matchesAt(content, 8, "WEBP".getBytes(StandardCharsets.US_ASCII));
            case "application/pdf" -> startsWith(content, "%PDF-".getBytes(StandardCharsets.US_ASCII));
            case "text/plain", "text/csv" -> isUtf8Text(content);
            case "application/zip" -> isZip(content);
            case "application/msword", "application/vnd.ms-excel", "application/vnd.ms-powerpoint" ->
                    startsWith(content, OLE_SIGNATURE);
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
                    isZip(content) && containsAscii(content, "word/");
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" ->
                    isZip(content) && containsAscii(content, "xl/");
            case "application/vnd.openxmlformats-officedocument.presentationml.presentation" ->
                    isZip(content) && containsAscii(content, "ppt/");
            default -> false;
        };
    }

    private boolean isUtf8Text(byte[] content) {
        for (byte value : content) {
            if (value == 0) return false;
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content));
            return true;
        } catch (CharacterCodingException exception) {
            return false;
        }
    }

    private boolean isZip(byte[] content) {
        return startsWith(content, new byte[]{0x50, 0x4B, 0x03, 0x04})
                || startsWith(content, new byte[]{0x50, 0x4B, 0x05, 0x06})
                || startsWith(content, new byte[]{0x50, 0x4B, 0x07, 0x08});
    }

    private boolean startsWith(byte[] content, byte[] signature) {
        return matchesAt(content, 0, signature);
    }

    private boolean matchesAt(byte[] content, int offset, byte[] expected) {
        if (content.length < offset + expected.length) return false;
        for (int index = 0; index < expected.length; index++) {
            if (content[offset + index] != expected[index]) return false;
        }
        return true;
    }

    private boolean containsAscii(byte[] content, String marker) {
        byte[] expected = marker.getBytes(StandardCharsets.US_ASCII);
        for (int offset = 0; offset <= content.length - expected.length; offset++) {
            if (matchesAt(content, offset, expected)) return true;
        }
        return false;
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 || dot == fileName.length() - 1 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String safeFileName(String originalName) {
        String candidate = originalName == null || originalName.isBlank() ? "upload" : originalName;
        try {
            Path path = Paths.get(candidate).getFileName();
            String fileName = path == null ? "" : path.toString()
                    .replaceAll("[\\p{Cntrl}\\\\/:*?\"<>|]", "_")
                    .replaceFirst("^\\.+", "");
            if (fileName.isBlank()) fileName = "upload";
            return fileName.length() <= 240 ? fileName : fileName.substring(fileName.length() - 240);
        } catch (InvalidPathException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file name");
        }
    }

    private FileResponse toResponse(StoredFileEntity entity) {
        return FileResponse.builder()
                .id(entity.getId())
                .originalName(entity.getOriginalName())
                .mimeType(entity.getMimeType())
                .sizeBytes(entity.getSizeBytes())
                .accessScope(entity.getAccessScope())
                .description(entity.getDescription())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
