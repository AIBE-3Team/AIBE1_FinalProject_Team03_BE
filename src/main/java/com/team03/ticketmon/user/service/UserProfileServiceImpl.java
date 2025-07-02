package com.team03.ticketmon.user.service;

import com.team03.ticketmon._global.config.supabase.SupabaseProperties;
import com.team03.ticketmon._global.util.FileValidator;
import com.team03.ticketmon._global.util.UploadPathUtil;
import com.team03.ticketmon._global.util.uploader.StorageUploader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    private final StorageUploader storageUploader;
    private final SupabaseProperties supabaseProperties;

    @Override
    public String uploadProfileAndReturnUrl(MultipartFile profileImage) {
        if (profileImage == null || profileImage.isEmpty()) {
            return "";
        }

        FileValidator.validate(profileImage);

        String fileUUID = UUID.randomUUID().toString();
        String contentType = profileImage.getContentType();
        String fileExtension = UploadPathUtil.getExtensionFromMimeType(contentType);

        String filePath = UploadPathUtil.getProfilePath(fileUUID, fileExtension);

        return storageUploader.uploadFile(profileImage, supabaseProperties.getProfileBucket(), filePath);
    }
}
