package com.team03.ticketmon._global.config.supabase;

//import io.supabase.storage.StorageClient; // 0.2.7
import io.supabase.StorageClient;   // 1.1.0
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Supabase Storage SDK를 초기화하고, StorageClient를 스프링 빈으로 등록하는 설정 클래스입니다.
 *
 * <p>SupabaseProperties에 정의된 설정 값(url, key)을 사용하여 StorageClient를 구성하며,
 * 이후 업로더 클래스(SupabaseUploader 등)에서 주입받아 파일 업로드에 활용됩니다.</p>
 *
 * <p>✅ 현재 프로젝트는 Supabase Auth를 사용하지 않으며, 모든 요청은 {@code anon} 키 기반의 서버 접근입니다.</p>
 */
@Configuration
@Profile("supabase")
@EnableConfigurationProperties(SupabaseProperties.class)
public class SupabaseConfig {

    private final SupabaseProperties supabaseProperties;

    public SupabaseConfig(SupabaseProperties supabaseProperties) {
        this.supabaseProperties = supabaseProperties;
    }

    /**
     * Supabase StorageClient를 빈으로 등록합니다.
     * - 이 StorageClient는 파일 업로드/삭제/다운로드 등에 사용됩니다.
     *
     * @return StorageClient 인스턴스
     */
    @Bean
    public StorageClient storageClient() {
        return new StorageClient(
                supabaseProperties.getUrl(),
                supabaseProperties.getKey()
        );
    }
}
