package com.team03.ticketmon._global.util;

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
        return MIME_TO_EXT.getOrDefault(mimeType, "bin");
    }
}
