package com.team03.ticketmon._global.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis 연결 상태 확인용 컨트롤러
 * - Redisson 연결 테스트
 * - 기본 Redis 동작 검증
 */
@Slf4j
@RestController
@RequestMapping("/health")
@RequiredArgsConstructor
public class RedisHealthController {

    private final RedissonClient redissonClient;

    /**
     * Redis 연결 상태 확인
     * GET /health/redis
     *
     * @return Redis 연결 성공/실패 메시지
     */
    @GetMapping("/redis")
    public ResponseEntity<Map<String, Object>> checkRedisConnection() {
        Map<String, Object> response = new HashMap<>();

        try {
            // Redis ping 테스트
            String testKey = "health:test:" + System.currentTimeMillis();
            String testValue = "Redis 연결 테스트 - " + LocalDateTime.now();

            // Redis에 데이터 저장
            RBucket<String> bucket = redissonClient.getBucket(testKey);
            bucket.set(testValue);

            // Redis에서 데이터 조회
            String retrievedValue = bucket.get();

            // 저장된 값과 조회된 값 비교
            if (testValue.equals(retrievedValue)) {
                response.put("status", "SUCCESS");
                response.put("message", "Redis 연결 성공");
                response.put("timestamp", LocalDateTime.now());
                response.put("testKey", testKey);
                response.put("testValue", retrievedValue);

                // 테스트 키 삭제
                bucket.delete();

                log.info("Redis 연결 테스트 성공: {}", testKey);
                return ResponseEntity.ok(response);
            } else {
                throw new RuntimeException("저장된 값과 조회된 값이 일치하지 않음");
            }

        } catch (Exception e) {
            response.put("status", "FAILURE");
            response.put("message", "Redis 연결 실패: " + e.getMessage());
            response.put("timestamp", LocalDateTime.now());

            log.error("Redis 연결 테스트 실패", e);
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Redis 서버 정보 조회
     * GET /health/redis/info
     *
     * @return Redis 서버 기본 정보
     */
    @GetMapping("/redis/info")
    public ResponseEntity<Map<String, Object>> getRedisInfo() {
        Map<String, Object> response = new HashMap<>();

        try {
            // RedissonClient가 활성 상태인지 확인
            // 아래 둘은 false가 정상 (Max! 안터져요~)
            boolean isShutdown = redissonClient.isShutdown();
            boolean isShuttingDown = redissonClient.isShuttingDown();

            response.put("status", "SUCCESS");
            response.put("isShutdown", isShutdown);
            response.put("isShuttingDown", isShuttingDown);
            response.put("timestamp", LocalDateTime.now());

            log.info("Redis 정보 조회 성공");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("status", "FAILURE");
            response.put("message", "Redis 정보 조회 실패: " + e.getMessage());
            response.put("timestamp", LocalDateTime.now());

            log.error("Redis 정보 조회 실패", e);
            return ResponseEntity.status(500).body(response);
        }
    }
}