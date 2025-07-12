package com.team03.ticketmon._global.service;

import com.team03.ticketmon._global.dto.UploadResponseDTO;
import com.team03.ticketmon._global.util.FileUtil;
import com.team03.ticketmon._global.util.StoragePathProvider;
import com.team03.ticketmon._global.util.uploader.StorageUploader;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Objects;

@Service
public class ExampleProfileImageService {

    @Qualifier("supabaseUploader") // SupabaseUploader를 명확하게 주입
    private final StorageUploader storageUploader;
    private final StoragePathProvider storagePathProvider; // StoragePathProvider 주입

    public ExampleProfileImageService(
            @Qualifier("supabaseUploader") StorageUploader storageUploader,
            StoragePathProvider storagePathProvider) {
        this.storageUploader = storageUploader;
        this.storagePathProvider = storagePathProvider;
    }

    public UploadResponseDTO uploadProfileImage(String uuid, MultipartFile file) {
        String fileExtension = FileUtil.getExtensionFromMimeType(Objects.requireNonNull(file.getContentType()));
        String path = storagePathProvider.getProfilePath(uuid, fileExtension); // StoragePathProvider에서 경로 생성
        String bucket = storagePathProvider.getProfileBucketName(); // StoragePathProvider에서 버킷 이름 가져오기
        String url = storageUploader.uploadFile(file, bucket, path);
        return new UploadResponseDTO(file.getOriginalFilename(), url);
    }
}
