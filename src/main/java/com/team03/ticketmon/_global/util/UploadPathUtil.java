package com.team03.ticketmon._global.util;

import java.net.URI;
import java.util.Map;

public class UploadPathUtil {

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
        String extension = MIME_TO_EXT.get(mimeType);
        if (extension == null) {
            throw new IllegalArgumentException("지원하지 않는 MIME 타입입니다: " + mimeType);
        }
        return extension;
    }

    public static String extractPathFromPublicUrl(String bucket, String publicUrl) {
        if (bucket == null || bucket.isEmpty() || publicUrl == null || publicUrl.isEmpty())
            return null;

        try {
            final String marker = "/storage/v1/object/public/" + bucket + "/";
            URI uri = URI.create(publicUrl);
            String path = uri.getPath();
            int idx = path.indexOf(marker);
            return (idx != -1) ? path.substring(idx + marker.length()) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
