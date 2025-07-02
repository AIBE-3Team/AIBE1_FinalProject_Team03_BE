package com.team03.ticketmon._global.config;

import java.util.Arrays;

import com.team03.ticketmon._global.util.RedisKeyGenerator;
import com.team03.ticketmon.auth.jwt.*;
import jakarta.servlet.DispatcherType;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.team03.ticketmon.auth.Util.CookieUtil;
import com.team03.ticketmon.auth.oauth2.OAuth2LoginFailureHandler;
import com.team03.ticketmon.auth.oauth2.OAuth2LoginSuccessHandler;
import com.team03.ticketmon.auth.service.CustomOAuth2UserService;
import com.team03.ticketmon.auth.service.RefreshTokenService;
import com.team03.ticketmon.auth.service.ReissueService;
import com.team03.ticketmon.user.service.SocialUserService;
import com.team03.ticketmon.user.service.UserEntityService;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * <b>Spring Security 설정 클래스</b>
 * <p>
 * 애플리케이션의 전반적인 보안(인증, 인가, CORS 등)을 담당합니다.
 * JWT 기반 인증 시스템을 사용하며, OAuth2 소셜 로그인도 지원합니다.
 * </p>
 */
@Configuration
@EnableWebSecurity  // Spring Security 활성화
@EnableMethodSecurity(  // 메서드 수준 보안 (예: @PreAuthorize) 활성화
	securedEnabled = true,  // @Secured 어노테이션 활성화
	prePostEnabled = true,  // @PreAuthorize, @PostAuthorize 어노테이션 활성화
	jsr250Enabled = true    // @RolesAllowed 어노테이션 활성화
)
@RequiredArgsConstructor
public class SecurityConfig {

	private final AuthenticationConfiguration authenticationConfiguration;
	private final JwtTokenProvider jwtTokenProvider;
	private final ReissueService reissueService;
	private final RefreshTokenService refreshTokenService;
	private final UserEntityService userEntityService;
	private final SocialUserService socialUserService;
	private final CookieUtil cookieUtil;
    private final RedissonClient redissonClient;
    private final RedisKeyGenerator keyGenerator;

	/**
	 * <b>AuthenticationManager 빈 설정</b> <br>
	 * Spring Security의 인증 처리를 담당하는 핵심 인터페이스입니다.
	 */
	@Bean
	public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {

		return configuration.getAuthenticationManager();
	}

	/**
	 * <b>PasswordEncoder 빈 설정</b> <br>
	 * 비밀번호 암호화 및 검증에 사용됩니다.
	 */
	@Bean
	public PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	/**
	 * <b>SecurityFilterChain 빈 설정</b> <br>
	 * HTTP 요청에 대한 보안 규칙을 정의합니다.
	 */
	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
			// CORS 설정: corsConfigurationSource 빈을 통해 허용 도메인 및 메서드를 정의
			.cors(cors -> cors.configurationSource(corsConfigurationSource()))

			// CSRF 보호 비활성화: JWT 기반 인증 시스템에서는 일반적으로 세션을 사용하지 않으므로 비활성화
			.csrf(AbstractHttpConfigurer::disable)

			// 세션 관리: JWT는 무상태(stateless)이므로 세션을 사용하지 않도록 설정
			.sessionManagement(session ->
				session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
			)

			// 기본 로그인 폼 비활성화: 자체 로그인 API를 사용하므로 Spring Security의 기본 폼 로그인 비활성화
			.formLogin(AbstractHttpConfigurer::disable)

			// HTTP Basic 인증 비활성화: 브라우저 팝업을 통한 기본 인증 방식 비활성화
			.httpBasic(AbstractHttpConfigurer::disable)

			// URL 별 접근 권한 설정
			.authorizeHttpRequests(auth -> auth

					//------------인증 없이 접근 허용할 경로들 (permitAll())------------
					// 로그인/회원가입/토큰 갱신 등 인증 관련 API 및 페이지
					.requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
					.requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/register/social").permitAll()    // 인증(로그인, 회원가입) 관련 API 경로 허용 (인증 불필요)
					.requestMatchers("/auth/**").permitAll() // login.html, register.html 등
					.requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll() // Swagger UI 및 API 문서

					// 콘서트 정보 조회 (목록, 검색, 필터링, 상세, AI 요약, 리뷰/기대평 목록) - 공개 API
					.requestMatchers(HttpMethod.GET, "/api/concerts", "/api/concerts/**").permitAll()
					.requestMatchers(HttpMethod.GET, "/api/concerts/{id}/**").permitAll() // 상세 조회, AI 요약
					.requestMatchers(HttpMethod.GET, "/api/concerts/{id}/reviews").permitAll() // 리뷰 목록 조회
					.requestMatchers(HttpMethod.GET, "/api/concerts/{id}/expectations").permitAll() // 기대평 목록 조회

					// 결제 콜백 및 웹훅 API (외부 시스템에서 호출하므로 permitAll)
					.requestMatchers("/api/v1/payments/success", "/api/v1/payments/fail").permitAll()
					.requestMatchers(HttpMethod.POST, "/api/v1/webhooks/toss/payment-updates").permitAll()

					// 기본 루트 URL
					.requestMatchers("/").permitAll()
					// .requestMatchers("/index.html").permitAll() // 필요시 주석 해제

					// 기타
					// .requestMatchers("/test/upload/**").permitAll()     // 파일 업로드 테스트용 API 경로 허용 (개발/테스트 목적)
					// .requestMatchers("/profile/image/**").permitAll()   // 프로필 이미지 접근/업로드 관련 API 경로 허용 (필요하다면 유지)

					//------------특정 역할이 필요한 경로들 (hasRole())------------
					// 관리자 전용 경로 - ADMIN 역할만 접근 허용 (관리자 페이지 및 API)
					.requestMatchers("/admin/**").hasRole("ADMIN")
					.requestMatchers("/api/admin/seats/**").hasRole("ADMIN")

					// 실제 판매자 기능 (콘서트 CRUD) - SELLER 역할만 접근 허용
					.requestMatchers("/api/seller/concerts/**").hasRole("SELLER")

					//결제 및 예매 API 경로 허용(로그인된 사용자일 시 )
					.requestMatchers("/api/v1/payments/history").authenticated() // 결제 내역 조회
					.requestMatchers(HttpMethod.POST, "/api/bookings").authenticated() // 예매 생성 및 결제 준비
					.requestMatchers(HttpMethod.POST, "/api/bookings/*/cancel").authenticated() // 예매 취소

					// 판매자 권한 신청 관련 API 경로 허용 (로그인된 사용자라면 누구나 접근 가능해야 함) - .anyRequest().authenticated()에 포함됨(주석처리)
					// .requestMatchers("/api/users/me/seller-status").authenticated() // 판매자 권한 UI 접근 시 로그인 사용자의 권한 상태 조회 (API-03-05)
					// .requestMatchers("/api/users/me/seller-requests").authenticated() // 판매자 권한 요청 등록 (API-03-06)
					// .requestMatchers("/api/users/me/role").authenticated() // 판매자 본인의 권한 철회 (API-03-07)

                    // ERROR 디스패치(서블릿이 sendError() 후 내부적으로 /error로 forward할 때)인 경우
                    // Spring Security 필터 체인을 건너뛰고, 원본 에러 상태(403 등)를 그대로 처리하도록 허용
                    .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()

					//------------나머지 모든 요청에 대한 접근 권한 설정 (authenticated())------------
					// 위에서 정의되지 않은 나머지 모든 요청은 인증(로그인)만 되면 접근 허용
					.anyRequest().authenticated()

				// <추후 추가될 수 있는 인가 설정>
				// .requestMatchers("/api/some-specific-path").hasAuthority("SOME_PERMISSION") // 특정 권한 필요
				// .requestMatchers("/api/public/**").permitAll() // 추가적인 공개 API 경로

				// 전체 인증 없이 API 테스트 가능(초기 개발 단계 / 추후 JWT 완성 시 주석 처리)
				// .anyRequest().permitAll()  // CORS 문제 임시 조치 -> 추후에 문제 해결 시 .anyRequest().authenticated() 활성화 예정
			)

			// OAuth2 Login
			.oauth2Login(oauth -> oauth
				.userInfoEndpoint(user -> user.userService(customOAuth2UserService()))
				.successHandler(oAuth2SuccessHandler())
				.failureHandler(oAuth2LoginFailureHandler()))

			// Login Filter 적용
			.addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider, reissueService, cookieUtil),
				LoginFilter.class)
			.addFilterBefore(new CustomLogoutFilter(jwtTokenProvider, refreshTokenService, cookieUtil),
				LogoutFilter.class)
			.addFilterAt(new LoginFilter(authenticationManager(authenticationConfiguration), cookieUtil),
				UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(new AccessKeyFilter(redissonClient, keyGenerator), JwtAuthenticationFilter.class)

			// 인증/인가 실패(인증 실패(401), 권한 부족(403)) 시 반환되는 예외 응답 설정
			.exceptionHandling(exception -> exception
				// 인증 실패 (401 Unauthorized) 시 처리
				.authenticationEntryPoint((request, response, authException) -> {
					response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);   // HTTP 401 상태 코드
					response.getWriter().write("Unauthorized: " + authException.getMessage());  // 응답 메시지
				})
				.accessDeniedHandler((request, response, accessDeniedException) -> {
					response.setStatus(HttpServletResponse.SC_FORBIDDEN);   // HTTP 403 상태 코드
					response.getWriter().write("Access Denied: " + accessDeniedException.getMessage()); // 응답 메시지

				})
			);

		return http.build();
	}

	/**
	 * <b>CORS 설정 빈</b> <br>
	 * 허용할 도메인, HTTP 메서드, 헤더, 자격 증명 등을 정의합니다.
	 */
	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration config = new CorsConfiguration();

		// 허용할 프론트엔드 도메인 (로컬 개발용 및 ngrok 주소)
		// 운영 환경 배포 시에는 실제 서비스 도메인으로 변경
		config.setAllowedOrigins(Arrays.asList(
			"http://localhost:3000",    // 기존 React App 기본 포트
			"http://localhost:8080",    // 백엔드 개발 서버 포트 (테스트용, 백엔드 직접 접근 시)
			"http://localhost:5173",    // Vite React 개발 서버 기본 포트 (새 프론트엔드 레포)
			"http://localhost:5174",    // <-- 이 부분 추가 (현재 프론트엔드 개발 서버 포트)
			"https://ff52-222-105-3-101.ngrok-free.app", // ngrok 등 터널링 서비스 주소 (필요 시)
			"https://localhost:3000"
		));

		// 허용할 HTTP 메서드
		config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

		// 요청 시 허용할 헤더  (인증 관련 헤더 포함)
		config.setAllowedHeaders(Arrays.asList(
			"Authorization", "Content-Type", "X-Requested-With", "Accept",
			"Origin", "X-CSRF-Token", "Cookie", "Set-Cookie", "X-Access-Key", "ngrok-skip-browser-warning"
		));

		// 인증 정보(쿠키, HTTP 인증 헤더) 포함한 요청 허용 (프론트엔드에서 credentials: 'include' 필요)
		config.setAllowCredentials(true);
		// Preflight 요청에 대한 캐시 유효 시간 (초)
		config.setMaxAge(3600L);

		// 위 설정을 전체 경로(/)에 적용
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);
		return source;
	}

	// OAuth2 로그인

	/**
	 * <b>Custom OAuth2UserService 빈 설정</b> <br>
	 * OAuth2 로그인 시 사용자 정보를 로드하고 처리합니다.
	 */
	@Bean
	public OAuth2UserService<OAuth2UserRequest, OAuth2User> customOAuth2UserService() {
		return new CustomOAuth2UserService(socialUserService, userEntityService);
	}

	/**
	 * <b>OAuth2LoginSuccessHandler 빈 설정</b> <br>
	 * OAuth2 로그인 성공 후 JWT 토큰 발행 및 쿠키 설정 등을 처리합니다.
	 */
	@Bean
	public OAuth2LoginSuccessHandler oAuth2SuccessHandler() {
		return new OAuth2LoginSuccessHandler(userEntityService, cookieUtil);
	}

	/**
	 * <b>OAuth2LoginFailureHandler 빈 설정</b> <br>
	 * OAuth2 로그인 실패 시 처리를 담당합니다.
	 */
	@Bean
	public OAuth2LoginFailureHandler oAuth2LoginFailureHandler() {
		return new OAuth2LoginFailureHandler();
	}

}