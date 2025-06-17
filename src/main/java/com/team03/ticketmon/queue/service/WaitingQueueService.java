package com.team03.ticketmon.queue.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Redis Sorted Set을 이용해 콘서트 대기열을 관리하는 서비스입니다.
 * 이 서비스는 대기열 추가, 순위 조회, 사용자 추출 등의 핵심 기능을 담당하며,
 * 모든 연산은 원자성(Atomic)을 보장해야 합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WaitingQueueService {

    private final RedissonClient redissonClient;
    private static final String QUEUE_KEY_PREFIX = "waitqueue:";

    /**
     * 특정 콘서트의 대기열에 사용자를 추가하고, 현재 대기 순번을 반환합니다.
     * 이미 대기열에 있는 사용자가 재요청 시, 순위는 변동되지 않습니다(멱등성 보장).
     *
     * @param concertId 대기열을 식별하는 콘서트 ID
     * @param userId    대기열에 추가할 사용자 ID
     * @return 1부터 시작하는 사용자의 대기 순번
     */
    public Long apply(Long concertId, String userId) {
        String queueKey = generateKey(concertId);
        RScoredSortedSet<String> queue = redissonClient.getScoredSortedSet(queueKey);

        // [핵심] 현재 시간을 점수(score)로 사용하여 사용자를 추가
        boolean isNewUser = queue.addIfAbsent(System.currentTimeMillis(), userId);

//        log.debug("대기열 신청 시도. 사용자: {}, 신규: {}, 대기열 키: {}", userId, isNewUser, queueKey);
        log.info("대기열 신청 시도. 사용자: {}, 신규: {}, 대기열 키: {}", userId, isNewUser, queueKey);

        Integer rankIndex = queue.rank(userId);

        if (rankIndex == null) {
            log.error("대기열 순위 조회 실패! 사용자가 정상적으로 추가되지 않았을 수 있습니다. 사용자: {}, 대기열 키: {}", userId, queueKey);
            throw new IllegalStateException("대기열 순위를 조회할 수 없습니다.");
        }

        // Redis의 rank는 0부터 시작, 사용자에게 친숙한 1-based 순위로 변환하여 반환
        return rankIndex.longValue() + 1;
    }

    /**
     * 대기열에서 가장 오래 기다린 사용자를 지정된 수만큼 추출(제거 후 반환).
     * 이 작업은 원자적으로(atomically) 이루어집니다.
     *
     * @param concertId 콘서트 ID
     * @param count     입장시킬 사용자 수
     * @return 입장 처리된 사용자 ID 리스트 (순서 보장)
     */
    public List<String> poll(Long concertId, int count) {
        RScoredSortedSet<String> queue = redissonClient.getScoredSortedSet(generateKey(concertId));

        // pollFirst(count)는 가장 점수가 낮은(오래된) N개의 원소를 Set에서 원자적으로 제거하고 반환
        Collection<String> polledItems = queue.pollFirst(count);

        // Collection을 List로 변환하여 반환 (일반적으로 순서가 보장됨)
        return new ArrayList<>(polledItems);
    }

    /**
     * 현재 대기열에 남아있는 총인원 수를 반환합니다.
     * @param concertId 콘서트 ID
     * @return 대기 인원 수
     */
    public Long getWaitingCount(Long concertId) {
        RScoredSortedSet<String> queue = redissonClient.getScoredSortedSet(generateKey(concertId));
        return (long) queue.size();
    }

    /**
     * 콘서트 ID를 기반으로 Redis에서 사용할 고유 키를 생성합니다.
     *
     * @param concertId 콘서트 ID
     * @return "waitqueue:{concertId}" 형식의 Redis 키
     */
    private String generateKey(Long concertId) {
        return QUEUE_KEY_PREFIX + concertId;
    }
}