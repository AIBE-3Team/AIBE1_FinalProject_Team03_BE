package com.team03.ticketmon._global.service;

import com.team03.ticketmon._global.dto.UploadResponseDTO;
import com.team03.ticketmon._global.util.uploader.StorageUploader;
import com.team03.ticketmon._global.util.FileValidator;
import com.team03.ticketmon._global.util.StoragePathProvider;
import com.team03.ticketmon._global.util.FileUtil;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Objects;
import java.util.UUID;

@Service
public class TestUploadService {

    private final StorageUploader storageUploader;
    private final StoragePathProvider storagePathProvider;

    public TestUploadService(
            @Qualifier("supabaseUploader") StorageUploader storageUploader,
            StoragePathProvider storagePathProvider) {
        this.storageUploader = storageUploader;
        this.storagePathProvider = storagePathProvider;
    }

    public UploadResponseDTO uploadTestFile(MultipartFile file) {
        // 파일 유효성 검사 추가: 파일 업로드 전에 FileValidator를 호출하여 유효성 검사(크기, 형식)를 수행합니다.
        // 이는 FileValidator 적용 예시를 제공하고, 누락될 수 있는 검증을 보완합니다.
        FileValidator.validate(file);

        String fileUUID = UUID.randomUUID().toString();
        String fileExtension = FileUtil.getExtensionFromMimeType(Objects.requireNonNull(file.getContentType()));
        // 테스트용 파일은 프로필 버킷에 임시 경로로 저장하도록 합니다.
        String path = storagePathProvider.getProfilePath("test/" + fileUUID, fileExtension);

        String bucket = storagePathProvider.getProfileBucketName(); // StoragePathProvider에서 버킷 이름 가져오기

        String uploadedUrl = storageUploader.uploadFile(file, bucket, path);

        // 클라이언트에 업로드된 파일명 + 공개 URL 반환
        return new UploadResponseDTO(file.getOriginalFilename(), uploadedUrl);
    }
}
