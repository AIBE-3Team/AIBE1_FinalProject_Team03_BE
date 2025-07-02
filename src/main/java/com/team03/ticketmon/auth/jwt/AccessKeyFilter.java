package com.team03.ticketmon.auth.jwt;

import com.team03.ticketmon._global.util.RedisKeyGenerator;
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
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class AccessKeyFilter extends OncePerRequestFilter {

    private final RedissonClient redissonClient;
    private final RedisKeyGenerator keyGenerator;

    private static final String ACCESS_KEY_HEADER = "X-Access-Key";

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final List<String> pathsToSecure = List.of(
            "/api/seats/concerts/{concertId}/**"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        Long concertId = getConcertIdIfSecurePath(request);
        if (concertId == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // 1. 인증 정보 확인 (Fail Test: 인증부터 확인)
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            log.error("인증 정보가 유효하지 않습니다. URI: {}", request.getRequestURI());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "인증 정보가 유효하지 않습니다.");
            return;
        }
        Long userId = userDetails.getUserId();

        // 2. AccessKey 헤더 확인
        String clientAccessKey = request.getHeader(ACCESS_KEY_HEADER);
        if (!StringUtils.hasText(clientAccessKey)) {
            log.warn("AccessKey가 헤더에 없습니다. URI: {}", request.getRequestURI());
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "접근 권한 증명(AccessKey)이 없습니다.");
            return;
        }

        // 3. Redis에서 AccessKey 조회
        String accessKeyRedisKey = keyGenerator.getAccessKey(concertId, userId);
        String serverAccessKey;

        try {
            RBucket<String> accessKeyBucket = redissonClient.getBucket(accessKeyRedisKey);
            serverAccessKey = accessKeyBucket.get();
        } catch (Exception e) {
            log.error("Redis에서 AccessKey 조회 실패. 사용자 ID: {}", hashUserId(userId), e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "시스템 오류가 발생했습니다.");
            return;
        }

        // 4. 키 비교 및 검증
        if (serverAccessKey == null || !serverAccessKey.equals(clientAccessKey)) {
            log.warn("AccessKey가 유효하지 않거나 만료되었습니다. 사용자 ID: {}", hashUserId(userId));
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "AccessKey가 유효하지 않거나 만료되었습니다.");
            return;
        }

        // 5. 검증 성공
        log.debug("AccessKey 검증 성공. 사용자 ID: {}", hashUserId(userId));
        filterChain.doFilter(request, response);
    }

    /**
     * 요청 URI가 보호 대상 경로에 해당하는지 확인하고, 해당하면 concertId를 추출하여 반환합니다.
     * @param request HttpServletRequest
     * @return 매칭되면 concertId, 아니면 null
     */
    private Long getConcertIdIfSecurePath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        for (String pattern : pathsToSecure) {
            if (pathMatcher.match(pattern, uri)) {
                try {
                    Map<String, String> variables = pathMatcher.extractUriTemplateVariables(pattern, uri);
                    return Long.parseLong(variables.get("concertId"));
                } catch (NumberFormatException e) {
                    log.warn("보호된 경로에서 concertId 추출 실패 (숫자 변환 오류): {}", uri);
                    return null; // 변환 실패 시
                } catch (Exception e) {
                    log.warn("보호된 경로에서 concertId 추출 실패: {}", uri, e);
                    return null;
                }
            }
        }
        return null; // 매칭되는 경로 없음
    }

    private String hashUserId(Long userId) {
        return "user_" + Integer.toHexString(userId.hashCode());
    }
}