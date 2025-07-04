package com.team03.ticketmon._global.util.uploader.s3;

import com.team03.ticketmon._global.util.uploader.StorageUploader;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * AWS S3 업로더 구현체 (마이그레이션 대비 스켈레톤)
 *
 * <p>
 * 이 클래스는 {@link StorageUploader} 인터페이스의 AWS S3 구현체입니다.
 * 현재는 마이그레이션 대비를 위한 스켈레톤 코드이며, 실제 S3 업로드 로직은 {@code TODO} 부분에 구현해야 합니다.
 * </p>
 *
 * <p>
 * {@code @Profile("s3")} 어노테이션이 적용되어 있어, 's3' 프로필이 활성화될 때만
 * Spring 컨테이너에 빈으로 등록됩니다. 이를 통해 SupabaseUploader와 동시에 활성화되어
 * 빈 충돌이 발생하는 것을 방지합니다.
 * </p>
 *
 * <p>
 * <b>사용 시점:</b> Supabase에서 AWS S3로 스토리지 서비스를 마이그레이션할 때,
 * 's3' 프로필을 활성화하여 이 업로더를 사용하도록 전환합니다.
 * </p>
 */
@Component
@Profile("s3")
public class S3Uploader implements StorageUploader {
    @Override
    public String uploadFile(MultipartFile file, String bucket, String path) {
        // TODO: S3 마이그레이션 시 실제 S3 업로드 로직을 구현
        throw new UnsupportedOperationException("아직 S3 업로드는 구현되지 않았습니다.");
    }

    @Override
    public void deleteFile(String bucket, String fullPath) {
        // TODO: S3 마이그레이션 시 실제 S3 파일 삭제 로직을 구현
        throw new UnsupportedOperationException("아직 S3 업로드는 구현되지 않았습니다.");
    }
}

