// src/main/java/com/team03/ticketmon/_global/config/RedissonConfig.java
package com.team03.ticketmon._global.config;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.redisson.codec.JsonJacksonCodec;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Redisson 설정 클래스
 * - Redis 분산 락, Pub/Sub, 캐시 기능을 위한 RedissonClient 설정
 * - Aiven Redis 서버 및 로컬 테스트 환경 연결 설정
 */
@Slf4j
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    @Value("${spring.data.redis.username:#{null}}")
    private String redisUsername;

    @Value("${spring.data.redis.password:#{null}}")
    private String redisPassword;

    @Value("${spring.data.redis.ssl.enabled:false}")
    private boolean sslEnabled;

    /**
     * RedissonClient Bean 설정
     * - spring.data.redis.ssl.enabled 값에 따라 프로토콜(redis:// 또는 rediss://) 결정
     * - destroyMethod = "shutdown" 추가로 애플리케이션 종료 시 안전한 리소스 해제
     */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();

        // 1) JSON 직렬화용 Codec 설정, Java 8 Date/Time 모듈 등록
        ObjectMapper om = new ObjectMapper()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .registerModule(new JavaTimeModule());
        // 필요에 따라 모듈 등록, 옵션 설정…
        config.setCodec(new JsonJacksonCodec(om));

        String protocol = sslEnabled ? "rediss" : "redis";
        String redisUrl = "%s://%s:%d".formatted(protocol, redisHost, redisPort);

        log.debug("Redisson Client를 생성합니다. Address: {}", redisUrl);

        // 단일 서버 모드 설정
        SingleServerConfig serverConfig = config.useSingleServer()
                .setAddress(redisUrl)
                .setConnectionMinimumIdleSize(1)    // 최소 유휴 연결 수
                .setConnectionPoolSize(10)          // 연결 풀 크기
                .setRetryAttempts(3)                // 재시도 횟수
                .setRetryInterval(1000)             // 재시도 간격 (ms)
                .setTimeout(3000);                  // 타임아웃 (ms)

        if (StringUtils.hasText(redisUsername)) {
            serverConfig.setUsername(redisUsername);
        }
        if (StringUtils.hasText(redisPassword)) {
            serverConfig.setPassword(redisPassword);
        }

        return Redisson.create(config);
    }
}