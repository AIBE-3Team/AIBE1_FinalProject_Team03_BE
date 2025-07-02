package com.team03.ticketmon._global.service;

import com.team03.ticketmon._global.dto.UploadResponseDTO;
import com.team03.ticketmon._global.util.uploader.StorageUploader;
import com.team03.ticketmon._global.util.UploadPathUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ExampleProfileImageService {

    private final StorageUploader storageUploader;

    public UploadResponseDTO uploadProfileImage(String uuid, MultipartFile file) {
        String fileExtension = getExtension(Objects.requireNonNull(file.getOriginalFilename()));
        String path = UploadPathUtil.getProfilePath(uuid, fileExtension);
        String url = storageUploader.uploadFile(file, "ticketmon-dev-profile-imgs", path);
        return new UploadResponseDTO(file.getOriginalFilename(), url);
    }

    private String getExtension(String fileName) {
        return fileName.substring(fileName.lastIndexOf('.') + 1);
    }
}
