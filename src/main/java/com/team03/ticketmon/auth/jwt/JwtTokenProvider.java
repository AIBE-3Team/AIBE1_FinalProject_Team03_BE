package com.team03.ticketmon.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@Component
public class JwtTokenProvider {

    public final String CATEGORY_ACCESS = "access";
    public final String CATEGORY_REFRESH = "refresh";
    private static final String CLAIM_CATEGORY = "category";
    private static final String CLAIM_USERID = "userid";
    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLE = "role";

    @Value("${jwt.secret}")
    private String secretKey;
    @Value("${jwt.access-expiration-ms}")
    private long accessExpirationMs;
    @Value("${jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    private SecretKey jwtSecretKey;

    @PostConstruct
    public void init() {
        this.jwtSecretKey = Keys.hmacShaKeyFor(secretKey.getBytes()); // JWT용 시크릿 키 변환
    }

    // JWT Token 생성
    public String generateToken(String category, Long userId, String username, String role) {
        Instant now = Instant.now();
        Date expiration = new Date(now.toEpochMilli() + getExpirationMs(category));

        return Jwts.builder()
                .claim(CLAIM_CATEGORY, category)
                .claim(CLAIM_USERID, userId)
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_ROLE, role)
                .issuedAt(Date.from(now))
                .expiration(expiration)
                .signWith(getSecretKey(), Jwts.SIG.HS256)
                .compact();
    }

    // JWT용 Secret Key 생성
    public SecretKey getSecretKey() {
        return this.jwtSecretKey;
    }

    public String getCategory(String token) {
        try {
            return parseClaims(token).get(CLAIM_CATEGORY, String.class);
        } catch (JwtException e) {
            throw new BadCredentialsException("JWT 토큰의 카테고리 형식이 유효하지 않습니다.", e);
        }
    }

    public Long getUserId(String token) {
        try {
            return parseClaims(token).get(CLAIM_USERID, Long.class);
        } catch (JwtException e) {
            throw new BadCredentialsException("JWT 토큰의 UserId 형식이 유효하지 않습니다.", e);
        }
    }

    public String getUsername(String token) {
        try {
            return parseClaims(token).get(CLAIM_USERNAME, String.class);
        } catch (JwtException e) {
            throw new BadCredentialsException("JWT 토큰의 Username 형식이 유효하지 않습니다.", e);
        }
    }

    @SuppressWarnings("unchecked")
    public List<String> getRoles(String token) {
        try {
            Object rolesObj = parseClaims(token).get(CLAIM_ROLE);

            if (rolesObj instanceof String roleStr) {
                // 단일 문자열이면 리스트로 감싸서 반환
                return List.of(roleStr);
            }

            if (rolesObj instanceof List<?> rolesList) {
                return rolesList.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .toList();
            }

            throw new BadCredentialsException("JWT 토큰의 roles 클레임 형식이 올바르지 않습니다: " + rolesObj);

        } catch (JwtException e) {
            throw new BadCredentialsException("토큰에서 역할 정보를 추출하는 데 실패했습니다.", e);
        }
    }

    // JWT Token 검증
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = parseClaims(token); // throw 되지 않으면 유효
        } catch (ExpiredJwtException e) {
            // 토큰은 만료되었지만 검증이 유효하므로 반환
          return true;
        } catch (JwtException | IllegalArgumentException e) {
            throw new BadCredentialsException("JWT 검증에 실패했습니다.", e);
        }
        return false;
    }

    public Authentication getAuthentication(String token) {
        Long userid = getUserId(token);
        String username = getUsername(token);
        List<SimpleGrantedAuthority> authorities = getRoles(token).stream()
                .map(SimpleGrantedAuthority::new)
                .toList();

        CustomUserDetails user = new CustomUserDetails(userid, username, "", "", authorities);
        return new UsernamePasswordAuthenticationToken(user, null, authorities);
    }


    // 공통 Claims Parser
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSecretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // 토큰별 만료 시간 값 가져오기
    public long getExpirationMs(String category) {
        long expirationMs = 0L;

        if (CATEGORY_ACCESS.equals(category)) {
            expirationMs = accessExpirationMs;
        } else if (CATEGORY_REFRESH.equals(category)) {
            expirationMs = refreshExpirationMs;
        }

        return expirationMs;
    }

    // 쿠키에서 토큰값 가져오기
    public String getTokenFromCookies(String category, HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;

        for (Cookie cookie : cookies) {
            if (category.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
