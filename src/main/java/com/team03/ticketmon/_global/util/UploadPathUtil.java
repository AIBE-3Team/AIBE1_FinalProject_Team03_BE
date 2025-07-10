package com.team03.ticketmon._global.util;

import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

public class UploadPathUtil {

    public static final String SUPABASE_PUBLIC_URL_PREFIX = "/storage/v1/object/public/";

    private static final Map<String, String> MIME_TO_EXT = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "application/pdf", "pdf"
    );

    public static String getProfilePath(String uuid, String fileExtension) {
        return String.format("user/profile/%s.%s", uuid, fileExtension);
    }

    public static String getPosterPath(Long concertId, String fileExtension) {
        return String.format("concert/poster/%d.%s", concertId, fileExtension);
    }

    public static String getSellerDocsPath(String uuid, String fileExtension) {
        return String.format("seller/docs/%s.%s", uuid, fileExtension);
    }

    // MIME 타입 기반 확장자 결정
    public static String getExtensionFromMimeType(String mimeType) {
        if (mimeType == null || !MIME_TO_EXT.containsKey(mimeType)) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE, "지원하지 않는 MIME 타입입니다: " + mimeType);
        }
        return MIME_TO_EXT.get(mimeType);
    }

    public static Optional<String> extractPathFromPublicUrl(String bucket, String publicUrl) {
        if (bucket == null || bucket.isEmpty() || publicUrl == null || publicUrl.isEmpty())
            return Optional.empty();

        try {
            final String marker = SUPABASE_PUBLIC_URL_PREFIX + bucket + "/";
            URI uri = URI.create(publicUrl);
            String path = uri.getPath();
            int idx = path.indexOf(marker);
            return (idx != -1) ? Optional.of(path.substring(idx + marker.length())) : Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
