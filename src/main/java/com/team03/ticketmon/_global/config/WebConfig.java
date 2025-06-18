package com.team03.ticketmon._global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * ✅ Web Configuration
 * <p>
 * 웹 관련 설정을 담당합니다 (CORS, 인터셉터 등).
 * </p>
 *
 * 📌 주요 설정:
 * <ul>
 *   <li>CORS 설정 (Cross-Origin Resource Sharing)</li>
 *   <li>향후 인터셉터, 포매터 등 추가 가능</li>
 * </ul>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

	/**
	 * CORS 설정
	 *
	 * 🚨 주의: 운영환경에서는 allowedOrigins("*")를 구체적인 도메인으로 제한해야 합니다.
	 */
	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/**")
			.allowedOrigins("*")  // 개발용: 모든 도메인 허용
			.allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
			.allowedHeaders("*")
			.maxAge(3600);  // 1시간 동안 preflight 결과 캐시
	}
}
