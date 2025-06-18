package com.team03.ticketmon._global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    /**
     * Swagger 전역 보안 설정<br>
     * - Bearer 방식의 JWT 인증을 테스트할 수 있도록 설정<br>
     * - Swagger Authorize 버튼 클릭 후 "Bearer <토큰>" 입력 가능
     */
    @Bean
    public OpenAPI openAPI() {
        final String securitySchemeName = "Authorization";

        return new OpenAPI()
                .info(new Info()
                        .title("Ticketing API")
                        .description("콘서트 예매 시스템 API 명세서\n\n 🔐 [주의] 현재는 인증 기능이 작동하지 않으므로 Authorize 버튼은 사용 불가합니다.")
                        .version("v1.0"))

                /**
                 * 💡 [주의] 현재 JWT 인증 필터는 구현되지 않았기 때문에
                 * Swagger Authorize 버튼을 눌러도 실제 인증은 동작하지 않습니다.
                 * 로그인/토큰 담당자가 JWT 필터를 구현한 후 연동됩니다.
                 */
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName) // 헤더 이름: Authorization
                                        .type(SecurityScheme.Type.HTTP) // HTTP 헤더 기반 인증
                                        .scheme("bearer")         // Authorization: Bearer {token}
                                        .bearerFormat("JWT")));         // 형식: JWT
    }



    //  API 그룹 나누기 예시 — Swagger UI에 구분된 그룹으로 표시됨 (현재는 계획 없음)
    // 주석 해제 시 사용 가능 (예: /api/user/**, /api/admin/** 등)
    // 사용자 API 그룹
//        @Bean
//        public GroupedOpenApi userApi() {
//                return GroupedOpenApi.builder()
//                        .group("User API")
//                        .pathsToMatch("/api/user/**")
//                        .packagesToScan("com.team03.ticketmongo") // ← 스캔 범위 지정(테스트용)
//                        .build();
//        }

    // 관리자 API 그룹
//        @Bean
//        public GroupedOpenApi adminApi() {
//                return GroupedOpenApi.builder()
//                        .group("Admin API") // Swagger UI에 표시될 그룹명
//                        .pathsToMatch("/api/admin/**")
//                        .build();
//        }
}