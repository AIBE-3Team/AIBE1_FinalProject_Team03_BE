package com.team03.ticketmon._global.util;

public class UploadPathUtil {

    public static String getProfilePath(Long userId, String fileExtension) {
        return String.format("user/profile/%d.%s", userId, fileExtension);
    }

    public static String getPosterPath(Long concertId, String fileExtension) {
        return String.format("concert/poster/%d.%s", concertId, fileExtension);
    }

    public static String getSellerDocsPath(String uuid, String fileExtension) {
        return String.format("seller/docs/%s.%s", uuid, fileExtension);
    }
}
