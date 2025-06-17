package com.team03.ticketmon._global.util.uploader.supabase;

import com.team03.ticketmon._global.util.uploader.StorageUploader;
import io.supabase.StorageClient;
//import io.supabase.common.SupabaseException;
import io.supabase.errors.StorageException; // 1.1.0
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

/**
 * Supabase Storage 업로더 구현체
 *
 * <p>{@link StorageUploader} 인터페이스를 구현하여 Supabase 스토리지에 파일을 업로드합니다.</p>
 *
 * <p>
 * 이 클래스는 `@Profile("supabase")` 어노테이션이 적용되어 있어,
 * 'supabase' 프로필이 활성화될 때 Spring 컨테이너에 빈으로 등록됩니다.
 * </p>
 */
@Component
@Profile("supabase") // 'supabase' 프로필이 활성화될 때 이 업로더를 사용
public class SupabaseUploader implements StorageUploader {

    private final StorageClient storageClient;

    public SupabaseUploader(StorageClient storageClient) {
        this.storageClient = storageClient;
    }

    /**
     * 주어진 파일을 Supabase Storage에 업로드한 후,
     * public URL을 반환합니다.
     *
     * @param file 업로드할 Multipart 파일
     * @param bucket Supabase 버킷 이름
     * @param path 업로드 경로 (디렉토리 형태)
     * @return 업로드된 파일의 퍼블릭 URL
     */
    @Override
    public String uploadFile(MultipartFile file, String bucket, String path) {
        try {
            String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
            String fullPath = path.endsWith("/") ? path + fileName : path + "/" + fileName;


            // → MultipartFile을 File 객체로 변환
            File convFile = File.createTempFile("upload-", "-" + fileName);
            file.transferTo(convFile);

            // Supabase에 파일 업로드 (File 사용)
            storageClient.from(bucket)
                    .upload(fullPath, convFile)
                    .get();  // Future 결과 기다림

            // 업로드된 파일의 퍼블릭 URL 반환
            return storageClient.from(bucket)
                    .getPublicUrl(fullPath, null, null)
                    .getPublicUrl();

        } catch (IOException | InterruptedException | ExecutionException e) {
            throw new RuntimeException("파일 업로드 중 시스템 오류", e);
        }
//        catch (StorageException e) {
//            throw new RuntimeException("Supabase 업로드 실패", e);
//        }
    }

}
