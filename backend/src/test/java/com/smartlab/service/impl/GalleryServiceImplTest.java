package com.smartlab.service.impl;

import com.smartlab.dto.request.CreateGalleryItemRequest;
import com.smartlab.entity.GalleryItemEntity;
import com.smartlab.entity.StoredFileEntity;
import com.smartlab.enums.GalleryItemStatus;
import com.smartlab.repo.EventRepository;
import com.smartlab.repo.GalleryItemRepository;
import com.smartlab.repo.ProjectRepository;
import com.smartlab.repo.StoredFileRepository;
import com.smartlab.repo.UserRepository;
import com.smartlab.service.FileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GalleryServiceImplTest {
    @Mock GalleryItemRepository galleryRepository;
    @Mock StoredFileRepository fileRepository;
    @Mock ProjectRepository projectRepository;
    @Mock EventRepository eventRepository;
    @Mock UserRepository userRepository;
    @Mock FileService fileService;

    @Test
    void publishChangesOnlyOwnedPrivateImageToPublic() {
        StoredFileEntity file = image(9L, "PRIVATE");
        GalleryItemEntity item = GalleryItemEntity.draft(file, "Workshop", null, "Workshop", null, null, null, null, false, null);
        ReflectionTestUtils.setField(item, "id", 4L);
        when(galleryRepository.findActiveByIdForUpdate(4L)).thenReturn(Optional.of(item));
        GalleryServiceImpl service = new GalleryServiceImpl(galleryRepository, fileRepository, projectRepository, eventRepository, userRepository, fileService);

        var response = service.publish(4L);

        assertThat(file.getAccessScope()).isEqualTo("PUBLIC");
        assertThat(item.getStatus()).isEqualTo(GalleryItemStatus.PUBLISHED);
        assertThat(response.fileId()).isEqualTo(9L);
    }

    @Test
    void refusesToPublishDeletedOrMissingFile() {
        GalleryItemEntity item = GalleryItemEntity.draft(null, "Workshop", null, "Workshop", null, null, null, null, false, null);
        when(galleryRepository.findActiveByIdForUpdate(4L)).thenReturn(Optional.of(item));
        GalleryServiceImpl service = new GalleryServiceImpl(galleryRepository, fileRepository, projectRepository, eventRepository, userRepository, fileService);

        assertThatThrownBy(() -> service.publish(4L)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void createAlwaysUsesPrivateUploadAndPersistsImageMetadata() {
        CreateGalleryItemRequest request = new CreateGalleryItemRequest();
        request.setTitle("Demo"); request.setAltText("Demo image");
        var uploaded = com.smartlab.dto.response.FileResponse.builder().id(22L).build();
        StoredFileEntity file = image(22L, "PRIVATE");
        when(fileService.upload(any(), org.mockito.ArgumentMatchers.eq("PRIVATE"), any(), any())).thenReturn(uploaded);
        when(fileRepository.findByIdAndDeletedAtIsNull(22L)).thenReturn(Optional.of(file));
        when(galleryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        GalleryServiceImpl service = new GalleryServiceImpl(galleryRepository, fileRepository, projectRepository, eventRepository, userRepository, fileService);

        var result = service.create(request, mock(org.springframework.web.multipart.MultipartFile.class), "admin@lab.test");

        assertThat(result.title()).isEqualTo("Demo");
        verify(fileService).upload(any(), org.mockito.ArgumentMatchers.eq("PRIVATE"), org.mockito.ArgumentMatchers.eq("Demo"), org.mockito.ArgumentMatchers.eq("admin@lab.test"));
    }

    private static StoredFileEntity image(Long id, String scope) {
        return StoredFileEntity.builder().id(id).originalName("demo.png").mimeType("image/png").sizeBytes(10L)
                .storageProvider("TEST").storageKey("demo").accessScope(scope).build();
    }
}
