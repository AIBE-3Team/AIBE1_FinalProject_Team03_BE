package com.team03.ticketmon._global.util.uploader.supabase;

import com.team03.ticketmon._global.exception.StorageUploadException;
import com.team03.ticketmon._global.util.UploadPathUtil;
import com.team03.ticketmon._global.util.uploader.StorageUploader;
import io.supabase.StorageClient;
// import io.supabase.common.SupabaseException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import lombok.extern.slf4j.Slf4j;


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
@Slf4j
@Component
@Profile("supabase") // 'supabase' 프로필이 활성화될 때 이 업로더를 사용
public class SupabaseUploader implements StorageUploader {

    private final StorageClient storageClient;

    public SupabaseUploader(StorageClient storageClient) {
        System.out.println("✅ SupabaseUploader 생성자 호출됨");
        System.out.println("✅ storageClient 클래스: " + storageClient.getClass());
        this.storageClient = storageClient;
    }

    /**
     * 주어진 파일을 Supabase Storage에 업로드한 후,
     * public URL을 반환합니다.
     *
     * @param file 업로드할 Multipart 파일
     * @param bucket Supabase 버킷 이름
     * @param finalUploadPath 서비스 레이어에서 결정된 최종 업로드 경로 (파일명과 확장자 포함)
     * @return 업로드된 파일의 퍼블릭 URL
     */
    @Override
    public String uploadFile(MultipartFile file, String bucket, String finalUploadPath) { // 변수명 변경: path -> finalUploadPath
        File tempFile = null;
        try {
            // finalUploadPath는 이미 UploadPathUtil에서 'seller/docs/UUID.확장자' 형태로 넘어옵니다.
            // 따라서 이 변수를 바로 fullPath로 사용해야 합니다.
            String fullPath = finalUploadPath; // 수정: 인자로 받은 finalUploadPath를 그대로 사용

            // 임시 파일 생성 시 확장자를 포함하여, OkHttp의 MediaType 추론을 돕습니다.
            String fileExtension = "";
            if (file.getOriginalFilename() != null && file.getOriginalFilename().contains(".")) {
                fileExtension = file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf('.')); // 점(.) 포함하여 확장자 추출
            }
            tempFile = File.createTempFile("upload-", fileExtension); // 수정: 임시 파일에 확장자 부여
            file.transferTo(tempFile);

            URI uri = tempFile.toURI();
            File safeFile = new File(uri);

            log.debug("✅ [DEBUG] SupabaseUploader 업로드 시작");
            log.debug("✅ [DEBUG] bucket = {}", bucket);
            log.debug("✅ [DEBUG] finalUploadPath (received) = {}", finalUploadPath);
            log.debug("✅ [DEBUG] fullPath (used for upload) = {}", fullPath);
            System.out.println("✅ safeFile.exists() = " + safeFile.exists());
            System.out.println("✅ safeFile path = " + safeFile.getAbsolutePath());


            // 업로드 요청
            // Supabase SDK는 파일 확장자를 기반으로 Content-Type을 추론합니다.
            // 임시 파일명에 올바른 확장자가 있으면 NullPointerException을 방지할 수 있습니다.
            storageClient.from(bucket)
                    .upload(fullPath, safeFile)
                    .get();

            String publicUrl = storageClient.from(bucket)
                    .getPublicUrl(fullPath, null, null)
                    .getPublicUrl();

            log.debug("✅ [DEBUG] public URL = {}", publicUrl);

            return publicUrl;

        } catch (IOException | InterruptedException | ExecutionException e) {
            log.error("❌ 파일 업로드 중 시스템 또는 기타 예외 발생", e);
            throw new StorageUploadException("파일 업로드 중 시스템 오류", e);// 변경
        }
        // catch (StorageException e) {
        //     throw new RuntimeException("Supabase 업로드 실패", e);
        // }
        finally {
            // 임시 파일 삭제
            if (tempFile != null && tempFile.exists()) {
                boolean deleted = tempFile.delete();
                if (deleted) {
                    log.debug("🧹 임시 파일 삭제 성공: {}", tempFile.getAbsolutePath());
                } else {
                    log.warn("❗ 임시 파일 삭제 실패: {}", tempFile.getAbsolutePath());
                }
            }
        }
    }

    @Override
    public void deleteFile(String bucket, String fullPath) {
        try {
            log.debug("✅ [DEBUG] SupabaseUploader 파일 삭제 시작");
            log.debug("✅ [DEBUG] bucket = {}", bucket);
            log.debug("✅ [DEBUG] fullPath = {}", fullPath);

            String deletePath = UploadPathUtil.extractPathFromPublicUrl(bucket, fullPath).orElse(null);
            log.debug("✅ [DEBUG] deletePath = {}", deletePath);

            if (deletePath == null || deletePath.isEmpty()) {
                log.warn("❗ Supabase 파일 삭제 실패: {}", fullPath);
                throw new IllegalArgumentException("파일 경로 형식이 잘못되었습니다.");
            }

            storageClient.from(bucket).delete(List.of(deletePath)).get(); // 비동기 실행 블록
            log.info("🗑️ Supabase 파일 삭제 성공: {}", fullPath);
        } catch (InterruptedException | ExecutionException e) {
            log.warn("❗ Supabase 파일 삭제 실패: {}", fullPath, e);
            throw new StorageUploadException("파일 삭제 실패", e);
        }
    }
}
