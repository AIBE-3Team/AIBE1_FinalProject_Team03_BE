package com.team03.ticketmon.concert.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Cache Service (제네릭 개선 버전)
 * 타입 안전한 캐싱 관련 비즈니스 로직 처리
 */
@Service
@RequiredArgsConstructor
public class CacheService {

	private final RedisTemplate<String, Object> redisTemplate;

	/**
	 * 제네릭 캐시 저장 메서드
	 * @param key 캐시 키
	 * @param data 저장할 데이터
	 * @param duration 만료 시간
	 * @param <T> 데이터 타입
	 */
	public <T> void setCache(String key, T data, Duration duration) {
		redisTemplate.opsForValue().set(key, data, duration);
	}

	/**
	 * 제네릭 캐시 조회 메서드
	 * @param key 캐시 키
	 * @param type 반환받을 데이터 타입 클래스
	 * @param <T> 데이터 타입
	 * @return Optional로 감싼 캐시 데이터
	 */
	@SuppressWarnings("unchecked")
	public <T> Optional<T> getCache(String key, Class<T> type) {
		Object cachedData = redisTemplate.opsForValue().get(key);
		if (cachedData != null && type.isInstance(cachedData)) {
			return Optional.of((T) cachedData);
		}
		return Optional.empty();
	}

	/**
	 * 콘서트 상세 정보 캐싱
	 */
	public <T> void cacheConcertDetail(Long concertId, T concertData) {
		String key = "concert:detail:" + concertId;
		setCache(key, concertData, Duration.ofMinutes(30));
	}

	/**
	 * 캐싱된 콘서트 상세 정보 조회 (타입 안전)
	 */
	public <T> Optional<T> getCachedConcertDetail(Long concertId, Class<T> type) {
		String key = "concert:detail:" + concertId;
		return getCache(key, type);
	}

	/**
	 * 검색 결과 캐싱
	 */
	public <T> void cacheSearchResults(String keyword, T searchResults) {
		String key = "search:" + keyword.toLowerCase();
		setCache(key, searchResults, Duration.ofMinutes(15));
	}

	/**
	 * Retrieves cached search results for the given keyword as a type-safe list.
	 *
	 * Returns an {@code Optional<List<T>>} containing the cached search results if present and all elements match the specified type; otherwise, returns {@code Optional.empty()}.
	 *
	 * @param keyword the search keyword used as the cache key
	 * @param elementType the class type of the expected list elements
	 * @return an {@code Optional} containing a list of elements of type {@code T} if available and type-safe; otherwise, {@code Optional.empty()}
	 */
	public <T> Optional<List<T>> getCachedSearchResults(String keyword, Class<T> elementType) {
		String key = "search:" + keyword.toLowerCase();
		Object cachedData = redisTemplate.opsForValue().get(key);
		if (cachedData instanceof List<?>) {
			List<?> list = (List<?>) cachedData;
	        List<T> typedList = list.stream()
	            .filter(elementType::isInstance)
	            .map(elementType::cast)
	            .collect(Collectors.toList());
			return typedList.isEmpty()
				? Optional.empty()
				: Optional.of(typedList);
		}
		return Optional.empty();
	}

	/**
	 * 사용자 예매 요약 캐싱
	 */
	public <T> void cacheUserBookingSummary(Long userId, T summary) {
		String key = "user:booking:summary:" + userId;
		setCache(key, summary, Duration.ofMinutes(10));
	}

	/**
	 * 캐싱된 사용자 예매 요약 조회 (타입 안전)
	 */
	public <T> Optional<T> getCachedUserBookingSummary(Long userId, Class<T> type) {
		String key = "user:booking:summary:" + userId;
		return getCache(key, type);
	}

	/**
	 * 캐시 무효화
	 */
	public void invalidateCache(String key) {
		redisTemplate.delete(key);
	}

	/**
	 * 패턴으로 캐시 무효화 (여러 키 삭제)
	 */
	public void invalidateCacheByPattern(String pattern) {
		var keys = redisTemplate.keys(pattern);
		if (keys != null && !keys.isEmpty()) {
			redisTemplate.delete(keys);
		}
	}

	/**
	 * 캐시 존재 여부 확인
	 */
	public boolean hasKey(String key) {
		return Boolean.TRUE.equals(redisTemplate.hasKey(key));
	}
}