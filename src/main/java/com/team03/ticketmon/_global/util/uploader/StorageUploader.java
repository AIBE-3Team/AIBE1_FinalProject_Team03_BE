package com.team03.ticketmon._global.util.uploader;

import org.springframework.web.multipart.MultipartFile;

/**
 * 스토리지 업로더 인터페이스
 *
 * <p>Supabase, S3 등 다양한 스토리지에 대응하기 위한 업로더 추상화 계층입니다.</p>
 */
public interface StorageUploader {

    /**
     * 파일을 업로드하고, 해당 파일의 public URL 또는 식별자를 반환합니다.
     *
     * @param file 업로드할 파일 (Multipart 형식)
     * @param bucket 버킷 이름 (예: profile 이미지, 포스터 등)
     * @param path 업로드 경로 (폴더 구조처럼 활용)
     * @return 업로드된 파일의 URL 또는 경로
     */
    String uploadFile(MultipartFile file, String bucket, String path);
    void deleteFile(String bucket, String fullPath);
}
