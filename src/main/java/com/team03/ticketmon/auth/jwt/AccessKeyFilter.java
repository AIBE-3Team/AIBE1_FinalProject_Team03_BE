package com.team03.ticketmon.auth.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class AccessKeyFilter extends OncePerRequestFilter {

    private final RedissonClient redissonClient;
    private static final String ACCESS_KEY_HEADER = "X-Access-Key";
    private static final String ACCESS_KEY_PREFIX = "accesskey:";

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final List<String> pathsToSecure = List.of(
            "/api/reserve/**",
            "/api/bookings/**",
            "/api/seats/concerts/*/seats/**"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        boolean isSecurePath = pathsToSecure.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, request.getRequestURI()));

        if (!isSecurePath) {
            filterChain.doFilter(request, response);
            return;
        }
        // 1. 요청 헤더에서 AccessKey 추출
        String clientAccessKey = request.getHeader(ACCESS_KEY_HEADER);

        // 2. AccessKey가 없으면 필터를 통과 (이후 로직에서 접근이 거부될 것임)
        if (!StringUtils.hasText(clientAccessKey)) {
            log.warn("AccessKey가 헤더에 없습니다. URI: {}", request.getRequestURI());
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "접근 권한 증명(AccessKey)이 없습니다.");
            return;
        }

        // 3. 현재 인증된 사용자 정보 가져오기
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            log.error("인증 정보가 유효하지 않습니다.");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "인증 정보가 유효하지 않습니다.");
            return;
        }
        Long userId = userDetails.getUserId();

        // 4. Redis에서 해당 사용자의 AccessKey 조회
        RBucket<String> accessKeyBucket = redissonClient.getBucket(ACCESS_KEY_PREFIX + userId);
        String serverAccessKey = accessKeyBucket.get();

        // 5. 클라이언트가 보낸 키와 서버에 저장된 키 비교
        if (serverAccessKey == null || !serverAccessKey.equals(clientAccessKey)) {
            log.warn("AccessKey가 유효하지 않거나 만료되었습니다. 사용자 ID: {}", userId);
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "AccessKey가 유효하지 않거나 만료되었습니다.");
            return;
        }

        // 6. 검증 성공 시, 다음 필터로 체인 계속
        log.info("AccessKey 검증 성공. 사용자 ID: {}", userId);
        filterChain.doFilter(request, response);
    }
}