package com.team03.ticketmon.user.service;

import com.team03.ticketmon._global.util.FileUtil;
import com.team03.ticketmon._global.util.FileValidator;
import com.team03.ticketmon._global.util.StoragePathProvider;
import com.team03.ticketmon._global.util.uploader.StorageUploader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    private final StorageUploader storageUploader;
    private final StoragePathProvider storagePathProvider;

    @Override
    public Optional<String> uploadProfileAndReturnUrl(MultipartFile profileImage) {
        if (profileImage == null || profileImage.isEmpty())
            return Optional.empty();

        FileValidator.validate(profileImage);

        String filePath = buildFilePath(profileImage);
        String fileUrl = storageUploader.uploadFile(profileImage, storagePathProvider.getProfileBucketName(), filePath);
        return Optional.of(fileUrl);
    }

    @Override
    public void deleteProfileImage(String profileImageUrl) {
        if (profileImageUrl == null || profileImageUrl.isEmpty())
            return;

        storageUploader.deleteFile(storagePathProvider.getProfileBucketName(), profileImageUrl);
    }

    private String buildFilePath(MultipartFile file) {
        String fileUUID = UUID.randomUUID().toString();
        String contentType = file.getContentType();
        String fileExtension = FileUtil.getExtensionFromMimeType(contentType);
        return storagePathProvider.getProfilePath(fileUUID, fileExtension);
    }
}
