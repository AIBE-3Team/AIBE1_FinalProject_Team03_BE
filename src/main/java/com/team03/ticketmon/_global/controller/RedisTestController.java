package com.team03.ticketmon._global.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Redis 기본 동작 테스트 컨트롤러
 * - String, Hash 자료구조 테스트
 * - TTL 기능 테스트
 */
@Slf4j
@RestController
@RequestMapping("/test/redis")
@RequiredArgsConstructor
public class RedisTestController {

    private final RedissonClient redissonClient;

    /**
     * Redis String 저장 테스트
     * POST /test/redis/string
     *
     * @param request 저장할 키-값 정보
     * @return 저장 결과
     */
    @PostMapping("/string")
    public ResponseEntity<Map<String, Object>> setString(@RequestBody RedisTestRequest request) {
        Map<String, Object> response = new HashMap<>();

        try {
            String key = "test:string:" + request.getKey();
            RBucket<String> bucket = redissonClient.getBucket(key);

            // TTL이 설정된 경우 TTL과 함께 저장, 아니면 영구 저장
            if (request.getTtlSeconds() != null && request.getTtlSeconds() > 0) {
                bucket.set(request.getValue(), request.getTtlSeconds(), TimeUnit.SECONDS);
                log.info("Redis String 저장 (TTL: {}초): {} = {}", request.getTtlSeconds(), key, request.getValue());
            } else {
                bucket.set(request.getValue());
                log.info("Redis String 저장 (영구): {} = {}", key, request.getValue());
            }

            response.put("status", "SUCCESS");
            response.put("message", "데이터 저장 성공");
            response.put("key", key);
            response.put("value", request.getValue());
            response.put("ttl", request.getTtlSeconds());
            response.put("timestamp", LocalDateTime.now());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("status", "FAILURE");
            response.put("message", "데이터 저장 실패: " + e.getMessage());
            response.put("timestamp", LocalDateTime.now());

            log.error("Redis String 저장 실패", e);
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Redis String 조회 테스트
     * GET /test/redis/string/{key}
     *
     * @param key 조회할 키
     * @return 조회 결과
     */
    @GetMapping("/string/{key}")
    public ResponseEntity<Map<String, Object>> getString(@PathVariable String key) {
        Map<String, Object> response = new HashMap<>();

        try {
            String fullKey = "test:string:" + key;
            RBucket<String> bucket = redissonClient.getBucket(fullKey);

            String value = bucket.get();
            long remainingTtl = bucket.remainTimeToLive(); // ms 단위

            response.put("status", "SUCCESS");
            response.put("key", fullKey);
            response.put("value", value);
            response.put("exists", value != null);
            response.put("remainingTtlMs", remainingTtl);
            response.put("remainingTtlSeconds", remainingTtl > 0 ? remainingTtl / 1000.0 : -1);
            response.put("timestamp", LocalDateTime.now());

            log.info("Redis String 조회: {} = {} (TTL: {}ms)", fullKey, value, remainingTtl);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("status", "FAILURE");
            response.put("message", "데이터 조회 실패: " + e.getMessage());
            response.put("timestamp", LocalDateTime.now());

            log.error("Redis String 조회 실패", e);
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Redis Hash 저장 테스트 (좌석 상태 관리용)
     * POST /test/redis/hash
     *
     * @param request Hash 필드 저장 요청
     * @return 저장 결과
     */
    @PostMapping("/hash")
    public ResponseEntity<Map<String, Object>> setHash(@RequestBody RedisHashTestRequest request) {
        Map<String, Object> response = new HashMap<>();

        try {
            String key = "test:hash:" + request.getHashKey();
            RMap<String, String> map = redissonClient.getMap(key);

            // Hash 필드에 값 저장
            map.put(request.getField(), request.getValue());

            response.put("status", "SUCCESS");
            response.put("message", "Hash 데이터 저장 성공");
            response.put("hashKey", key);
            response.put("field", request.getField());
            response.put("value", request.getValue());
            response.put("timestamp", LocalDateTime.now());

            log.info("Redis Hash 저장: {}[{}] = {}", key, request.getField(), request.getValue());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("status", "FAILURE");
            response.put("message", "Hash 데이터 저장 실패: " + e.getMessage());
            response.put("timestamp", LocalDateTime.now());

            log.error("Redis Hash 저장 실패", e);
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Redis Hash 전체 조회 테스트
     * GET /test/redis/hash/{hashKey}
     *
     * @param hashKey 조회할 Hash 키
     * @return Hash 전체 데이터
     */
    @GetMapping("/hash/{hashKey}")
    public ResponseEntity<Map<String, Object>> getHash(@PathVariable String hashKey) {
        Map<String, Object> response = new HashMap<>();

        try {
            String fullKey = "test:hash:" + hashKey;
            RMap<String, String> map = redissonClient.getMap(fullKey);

            Map<String, String> allData = map.readAllMap();

            response.put("status", "SUCCESS");
            response.put("hashKey", fullKey);
            response.put("data", allData);
            response.put("size", allData.size());
            response.put("timestamp", LocalDateTime.now());

            log.info("Redis Hash 조회: {} = {} (크기: {})", fullKey, allData, allData.size());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("status", "FAILURE");
            response.put("message", "Hash 데이터 조회 실패: " + e.getMessage());
            response.put("timestamp", LocalDateTime.now());

            log.error("Redis Hash 조회 실패", e);
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Redis 키 삭제 테스트
     * DELETE /test/redis/{type}/{key}
     *
     * @param type 데이터 타입 (string, hash)
     * @param key 삭제할 키
     * @return 삭제 결과
     */
    @DeleteMapping("/{type}/{key}")
    public ResponseEntity<Map<String, Object>> deleteKey(@PathVariable String type, @PathVariable String key) {
        Map<String, Object> response = new HashMap<>();

        try {
            String fullKey = "test:" + type + ":" + key;
            boolean deleted = redissonClient.getBucket(fullKey).delete();

            response.put("status", "SUCCESS");
            response.put("message", deleted ? "키 삭제 성공" : "키가 존재하지 않음");
            response.put("key", fullKey);
            response.put("deleted", deleted);
            response.put("timestamp", LocalDateTime.now());

            log.info("Redis 키 삭제: {} (삭제됨: {})", fullKey, deleted);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("status", "FAILURE");
            response.put("message", "키 삭제 실패: " + e.getMessage());
            response.put("timestamp", LocalDateTime.now());

            log.error("Redis 키 삭제 실패", e);
            return ResponseEntity.status(500).body(response);
        }
    }

    // DTO 클래스들
    public static class RedisTestRequest {
        private String key;
        private String value;
        private Integer ttlSeconds;

        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        public Integer getTtlSeconds() { return ttlSeconds; }
        public void setTtlSeconds(Integer ttlSeconds) { this.ttlSeconds = ttlSeconds; }
    }

    public static class RedisHashTestRequest {
        private String hashKey;
        private String field;
        private String value;

        public String getHashKey() { return hashKey; }
        public void setHashKey(String hashKey) { this.hashKey = hashKey; }
        public String getField() { return field; }
        public void setField(String field) { this.field = field; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }
}