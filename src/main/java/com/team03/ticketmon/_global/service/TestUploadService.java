package com.team03.ticketmon._global.service;

import com.team03.ticketmon._global.dto.UploadResponseDTO;
import com.team03.ticketmon._global.util.uploader.StorageUploader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class TestUploadService {

    private final StorageUploader storageUploader;

    public UploadResponseDTO uploadTestFile(MultipartFile file) {
        // TODO: 나중에 유효성 검사(FileValidator) 붙이기

        // 테스트용 버킷/경로 하드코딩 (후에 동적으로 바꿔도 됨)
        String bucket = "ticketmon-dev-profile-imgs";
//        String path = "test/" + file.getOriginalFilename();
        String path = "test";

        // SupabaseUploader → 실제 업로드
        String uploadedUrl = storageUploader.uploadFile(file, bucket, path);

        // 클라이언트에 업로드된 파일명 + 공개 URL 반환
        return new UploadResponseDTO(file.getOriginalFilename(), uploadedUrl);
    }
}
