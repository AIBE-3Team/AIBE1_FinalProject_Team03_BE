package com.team03.ticketmon._global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ✅ CORS 설정 프로퍼티 클래스
 * <p>
 * application.yml 또는 application.properties 파일에 정의된
 * cors.allowed-origins 값을 주입받아 관리합니다.
 * </p>
 *
 * 📌 주요 설정:
 * <ul>
 *   <li>프론트엔드에서 접근 허용할 도메인(origin) 목록 정의</li>
 *   <li>배열(String[]) 또는 리스트(List&lt;String&gt;)로 구성 가능</li>
 *   <li>WebSocket 및 Spring Security CORS 설정에서 재사용</li>
 * </ul>
 *
 * 📁 설정 예시 (application.yml):
 * <pre>
 * cors:
 *   allowed-origins:
 *     - http://localhost:3000
 *     - https://mydomain.com
 * </pre>
 */

@ConfigurationProperties(prefix = "cors")
public class CorsProperties {
    private String[] allowedOrigins;

    public String[] getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
}