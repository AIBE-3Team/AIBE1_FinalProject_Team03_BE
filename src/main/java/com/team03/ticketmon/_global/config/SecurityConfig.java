package com.team03.ticketmon._global.config;

import com.team03.ticketmon.auth.Util.CookieUtil;
import com.team03.ticketmon.auth.jwt.JwtAuthenticationFilter;
import com.team03.ticketmon.auth.jwt.JwtTokenProvider;
import com.team03.ticketmon.auth.jwt.LoginFilter;
import com.team03.ticketmon.auth.jwt.CustomLogoutFilter;
import com.team03.ticketmon.auth.oauth2.OAuth2LoginFailureHandler;
import com.team03.ticketmon.auth.oauth2.OAuth2LoginSuccessHandler;
import com.team03.ticketmon.auth.service.CustomOAuth2UserService;
import com.team03.ticketmon.auth.service.RefreshTokenService;
import com.team03.ticketmon.auth.service.ReissueService;
import com.team03.ticketmon.user.service.SocialUserService;
import com.team03.ticketmon.user.service.UserEntityService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(
        securedEnabled = true,
        prePostEnabled = true,
        jsr250Enabled = true
)
@RequiredArgsConstructor
public class SecurityConfig {

    // 🔐 JWT 필터 자리 확보 (JWT 인증 필터는 로그인/토큰 담당자가 구현 예정)
    // 구현 후 아래 필터 삽입 코드의 주석을 해제하면 Security와 연동됩니다.
    private final AuthenticationConfiguration authenticationConfiguration;
    private final JwtTokenProvider jwtTokenProvider;
    private final ReissueService reissueService;
    private final RefreshTokenService refreshTokenService;
    private final UserEntityService userEntityService;
    private final SocialUserService socialUserService;
    private final CookieUtil cookieUtil;

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {

        return configuration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))  // 프론트엔드 요청(CORS) 허용
                .csrf(AbstractHttpConfigurer::disable)  // CSRF 토큰 비활성화 (JWT 기반 인증 시스템에서는 사용 안 함)
                .sessionManagement(session ->   // 세션 사용하지 않는 무상태(stateless) 서버 설정
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .formLogin(AbstractHttpConfigurer::disable) // 기본 로그인 폼("/login") 비활성화 -> 우리는 자체 로그인 api 사용 예정
                .httpBasic(AbstractHttpConfigurer::disable) // 브라우저 팝업 로그인 방식 (HTTP Basic 인증)도 비활성화
                .authorizeHttpRequests(auth -> auth // URL 별 접근 권한 설정
                                // 인증 없이 접근 허용할 경로들 (프론트 페이지, Swagger 문서, Auth 관련(로그인/회원가입) 등
                                .requestMatchers("/", "/index.html").permitAll()
                                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                                .requestMatchers("/api/auth/**").permitAll()

                                // Supabase 업로드 테스트용 API 경로 허용 (개발 및 테스트 목적)
                                // 실제 배포 시에는 적절한 인증/인가 로직 또는 제한된 IP 접근 등으로 보안 강화 필요
                                .requestMatchers("/test/upload/**").permitAll()
                                .requestMatchers("/profile/image/**").permitAll()

                                // 관리자 전용 경로 (ADMIN 권한 필요)
                                // 나중에 권한 로직 추가(JWT 구현) 후 권한이 부여되면 주석 해제
                                // .requestMatchers("/admin/**").hasRole("ADMIN")

                                // 전체 인증 없이 API 테스트 가능(초기 개발 단계 / 추후 JWT 완성 시 주석 처리)
                                .anyRequest().permitAll()
                        // 나머지 모든 요청은 인증만 되면 접근 허용 (추후 JWT 완성 시 주석 제거)
//                        .anyRequest().authenticated()
                )
                // OAuth2 Login
                .oauth2Login(oauth -> oauth
                        .userInfoEndpoint(user -> user.userService(customOAuth2UserService()))
                        .successHandler(oAuth2SuccessHandler())
                        .failureHandler(oAuth2LoginFailureHandler()))

                // Login Filter 적용
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider, reissueService, cookieUtil), LoginFilter.class)
                .addFilterBefore(new CustomLogoutFilter(jwtTokenProvider, refreshTokenService, cookieUtil), LogoutFilter.class)
                .addFilterAt(new LoginFilter(authenticationManager(authenticationConfiguration), jwtTokenProvider, refreshTokenService, cookieUtil), UsernamePasswordAuthenticationFilter.class)

                // 인증/인가 실패(인증 실패(401), 권한 부족(403)) 시 반환되는 예외 응답 설정
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);   // 401
                            response.getWriter().write("Unauthorized: " + authException.getMessage());
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);   // 403
                            response.getWriter().write("Access Denied: " + accessDeniedException.getMessage());
                        })
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // 허용할 프론트엔드 도메인 (로컬 개발용)
        config.setAllowedOrigins(Arrays.asList(
                "http://localhost:3000",
                "http://localhost:8080",
                "https://ff52-222-105-3-101.ngrok-free.app"
        ));

        // 허용할 HTTP 메서드
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

        // 요청 시 허용할 헤더
        config.setAllowedHeaders(Arrays.asList(
                "Authorization", "Content-Type", "X-Requested-With", "Accept",
                "Origin", "X-CSRF-Token", "Cookie", "Set-Cookie"
        ));

        // 인증 정보 포함한 요청 허용 (credentials: true)
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        // 위 설정을 전체 경로(/)에 적용
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    // OAuth2 로그인
    @Bean
    public OAuth2UserService<OAuth2UserRequest, OAuth2User> customOAuth2UserService() {
        return new CustomOAuth2UserService(socialUserService, userEntityService);
    }

    @Bean
    public OAuth2LoginSuccessHandler oAuth2SuccessHandler() {
        return new OAuth2LoginSuccessHandler(userEntityService, refreshTokenService, jwtTokenProvider, cookieUtil);
    }

    @Bean
    public OAuth2LoginFailureHandler oAuth2LoginFailureHandler() {
        return new OAuth2LoginFailureHandler();
    }

}
