package com.team03.ticketmon.user.service;

import com.team03.ticketmon._global.config.supabase.SupabaseProperties;
import com.team03.ticketmon._global.util.FileValidator;
import com.team03.ticketmon._global.util.UploadPathUtil;
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
    private final SupabaseProperties supabaseProperties;

    @Override
    public Optional<String> uploadProfileAndReturnUrl(MultipartFile profileImage) {
        if (profileImage == null || profileImage.isEmpty())
            return Optional.empty();

        FileValidator.validate(profileImage);

        String filePath = buildFilePath(profileImage);
        String fileUrl = storageUploader.uploadFile(profileImage, supabaseProperties.getProfileBucket(), filePath);
        return Optional.of(fileUrl);
    }

    @Override
    public void deleteProfileImage(String profileImageUrl) {
        if (profileImageUrl == null || profileImageUrl.isEmpty())
            return;

        storageUploader.deleteFile(supabaseProperties.getProfileBucket(), profileImageUrl);
    }

    private String buildFilePath(MultipartFile file) {
        String fileUUID = UUID.randomUUID().toString();
        String contentType = file.getContentType();
        String fileExtension = UploadPathUtil.getExtensionFromMimeType(contentType);
        return UploadPathUtil.getProfilePath(fileUUID, fileExtension);
    }
}
