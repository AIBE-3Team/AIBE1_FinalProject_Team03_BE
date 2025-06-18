package com.team03.ticketmon._global.config.supabase;

//import io.supabase.storage.StorageClient; // 0.2.7
import io.supabase.StorageClient;   // 1.1.0
import lombok.RequiredArgsConstructor;
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
 * <p>✅ 현재 프로젝트는 Supabase Auth를 사용하지 않으며, 모든 요청은 **{@code service_role} 키** 기반의 서버 접근입니다.</p>
 * <p>⚠️ **주의:** {@code service_role} 키는 강력한 권한을 가지므로, 절대 클라이언트(브라우저)에 노출되어서는 안 됩니다.</p>
 */
@Configuration
@Profile("supabase")
@EnableConfigurationProperties(SupabaseProperties.class)
@RequiredArgsConstructor
public class SupabaseConfig {

    private final SupabaseProperties supabaseProperties;

    /**
     * Supabase StorageClient를 빈으로 등록합니다. <br>
     * - 이 StorageClient는 파일 업로드/삭제/다운로드 등에 사용됩니다.
     *
     * @return StorageClient 인스턴스
     */
    @Bean
    public StorageClient storageClient() {
        String baseUrl = supabaseProperties.getUrl();
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }

        // Supabase Storage API의 표준 경로인 '/storage/v1/'를 추가
        String storageUrl = baseUrl + "storage/v1/";

        System.out.println("✅ [DEBUG] Supabase storageUrl = " + storageUrl);
        // 보안상 전체 키를 출력하지 않고, 앞 몇 글자만 출력해서 확인
        System.out.println("✅ [DEBUG] Supabase key (first 5 chars) = " + supabaseProperties.getKey().substring(0, Math.min(supabaseProperties.getKey().length(), 5)));

        // StorageClient 생성자 인자 순서는 (apiKey, url) 입니다.
        // 과거 버전(0.2.7)에서는 (url, apiKey) 순서였으나, 1.1.0 버전에서는 (apiKey, url) 순서입니다.
        // 이 순서가 잘못되면 'InvalidKey' 또는 'Expected URL scheme' 오류가 발생할 수 있음(주의!)
        return new StorageClient(supabaseProperties.getKey(), storageUrl);
    }
}
