package com.team03.ticketmon._global.util;

import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public class FileValidator {

    // 변경: 기존 2MB에서 10MB로 파일 크기 제한을 상향 조정했습니다.
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final List<String> ALLOWED_TYPES = List.of("image/jpeg", "image/png", "image/webp", "application/pdf");

    public static void validate(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "파일이 비어 있습니다.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            // 변경: 파일 크기 제한 초과 시 발생하는 예외 메시지를 10MB로 수정했습니다.
            throw new BusinessException(ErrorCode.FILE_SIZE_LIMIT_EXCEEDED, "파일 크기는 10MB를 초과할 수 없습니다.");
        }

        if (file.getContentType() == null) {
            throw new BusinessException(ErrorCode.FILE_CONTENT_TYPE_NULL);
        }

        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE, "허용되지 않은 파일 형식입니다: " + file.getContentType());
        }
    }
}
