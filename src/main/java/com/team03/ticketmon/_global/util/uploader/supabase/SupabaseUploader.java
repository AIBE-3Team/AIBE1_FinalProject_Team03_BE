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
import java.net.URI;
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
     * @param path 업로드 경로 (디렉토리 형태)
     * @return 업로드된 파일의 퍼블릭 URL
     */
    @Override
    public String uploadFile(MultipartFile file, String bucket, String path) {
        File tempFile = null; // finally 블록에서 접근 가능하도록 선언
        try {
            String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
            String fullPath = path + "/" + fileName;

            // 디버깅용 Supabase 정보 출력
            log.debug("✅ [DEBUG] SupabaseUploader 업로드 시작");
            log.debug("✅ [DEBUG] bucket = {}", bucket);
            log.debug("✅ [DEBUG] path = {}", path);
            log.debug("✅ [DEBUG] fileName = {}", fileName);
            log.debug("✅ [DEBUG] fullPath = {}", fullPath);

            // 원본 임시 파일
            tempFile = File.createTempFile("upload-", "-" + fileName); // tempFile 할당
            file.transferTo(tempFile);

            // File → URI → File 재생성 (경로 깨짐 방지 목적, 필요 없으면 제거 가능)
            URI uri = tempFile.toURI();
            File safeFile = new File(uri);

            System.out.println("✅ safeFile.exists() = " + safeFile.exists());
            System.out.println("✅ safeFile path = " + safeFile.getAbsolutePath());

            // 업로드 요청
            storageClient.from(bucket)
                    .upload(fullPath, safeFile)
                    .get();

            String publicUrl = storageClient.from(bucket)
                    .getPublicUrl(fullPath, null, null)
                    .getPublicUrl();

            log.debug("✅ [DEBUG] public URL = {}", publicUrl);
            return publicUrl;

        } catch (IOException | InterruptedException | ExecutionException e) {
            // 파일 I/O, 스레드 인터럽트, 비동기 작업 실행 예외 처리
            log.error("❌ 파일 업로드 중 시스템 또는 기타 예외 발생", e);
            throw new RuntimeException("파일 업로드 중 시스템 오류", e);
        }
//        catch (StorageException e) {
//            throw new RuntimeException("Supabase 업로드 실패", e);
//        }
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

}
