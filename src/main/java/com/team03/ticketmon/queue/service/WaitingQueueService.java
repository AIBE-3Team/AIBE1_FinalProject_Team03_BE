package com.team03.ticketmon.queue.service;

import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;
import com.team03.ticketmon._global.util.RedisKeyGenerator;
import com.team03.ticketmon.concert.service.ConcertService;
import com.team03.ticketmon.queue.dto.EnterResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.LongCodec;
import org.springframework.stereotype.Service;

import java.time.Duration;
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
    private final ConcertService concertService;
    private final AdmissionService admissionService;
    private final RedisKeyGenerator keyGenerator;

    private static final String SEQUENCE_KEY_SUFFIX = ":seq:";

    private static final int SEQUENCE_BITS = 21;
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;

    /**
     * 특정 콘서트의 대기열에 사용자를 추가하고, 현재 대기 순번을 반환합니다.
     * 타임스탬프와 원자적 시퀀스를 조합한 유니크한 점수를 사용해 공정성을 보장합니다.
     *
     * @param concertId 대기열을 식별하는 콘서트 ID
     * @param userId    대기열에 추가할 사용자 ID
     * @return 1부터 시작하는 사용자의 대기 순번
     */
    public EnterResponse apply(Long concertId, Long userId) {

        // [1단계] 콘서트 정보를 조회하여 대기열 활성화 여부 확인
        boolean isQueueActive = concertService.isQueueActive(concertId);

        if (isQueueActive) {
            // [2-1단계] 대기열이 활성화된 경우: 기존 대기열 등록 로직 수행
            String queueKey = generateKey(concertId);
            RScoredSortedSet<Long> queue = redissonClient.getScoredSortedSet(queueKey, LongCodec.INSTANCE);

            long uniqueScore = generateUniqueScore(queueKey);

            if (!queue.addIfAbsent(uniqueScore, userId)) {
                log.warn("[userId: {}] 이미 대기열에 등록된 상태", userId);
                Integer existingRank = queue.rank(userId);
                if (existingRank != null) {
                    return EnterResponse.waiting(existingRank.longValue() + 1);
                }
                throw new BusinessException(ErrorCode.QUEUE_ALREADY_JOINED);
            }

            log.debug("[userId: {}] 대기열 신규 신청. [콘서트: {}, 대기열 키: {}, 부여된 점수: {}]", userId, concertId, queueKey, uniqueScore);
            Integer rankIndex = queue.rank(userId);

            if (rankIndex == null) {
                log.error("[userId: {}] 대기열 순위 조회 실패", userId);
                throw new BusinessException(ErrorCode.SERVER_ERROR);
            }

            return EnterResponse.waiting(rankIndex.longValue() + 1);

        } else {
            // [2-2단계] 대기열이 비활성화된 경우: 즉시 입장 처리
            log.debug("[userId: {}] 대기열 비활성 상태. 즉시 입장 처리 시작.", userId);

            return EnterResponse.immediateEntry(admissionService.grantAccess(concertId, userId));
        }

    }

    /**
     * 타임스탬프와 원자적 시퀀스를 조합하여 유니크한 score를 생성합니다.
     * 예: (timestamp << 22) | sequence
     * @param queueKey queueKey
     * @return 유니크한 score(long)
     */
    private long generateUniqueScore(String queueKey) {
        long timestamp = System.currentTimeMillis();

        // 1ms 단위로 시퀀스 키를 생성하여 자동으로 초기화되도록 함
        String sequenceKey = queueKey + SEQUENCE_KEY_SUFFIX + timestamp;
        RAtomicLong sequence = redissonClient.getAtomicLong(sequenceKey);

        // 키가 처음 생성될 때 2초의 만료 시간을 설정 (메모리 릭 방지)
        sequence.expire(Duration.ofSeconds(2));

        long currentSequence = sequence.incrementAndGet();

        if (currentSequence > MAX_SEQUENCE) {
            log.error("1ms 내 요청 한도 초과! ({}개 이상)", MAX_SEQUENCE);
            throw new BusinessException(ErrorCode.QUEUE_TOO_MANY_REQUESTS);
        }

        // 타임스탬프를 왼쪽으로 22비트 이동시키고, 시퀀스 번호를 OR 연산으로 합침
        return (timestamp << SEQUENCE_BITS) | currentSequence;
    }

    /**
     * 대기열에서 가장 오래 기다린 사용자를 지정된 수만큼 추출(제거 후 반환).
     * 이 작업은 원자적으로(atomically) 이루어집니다.
     *
     * @param concertId 콘서트 ID
     * @param count     입장시킬 사용자 수
     * @return 입장 처리된 사용자 ID 리스트 (순서 보장)
     */
    public List<Long> poll(Long concertId, int count) {
        RScoredSortedSet<Long> queue = redissonClient.getScoredSortedSet(generateKey(concertId), LongCodec.INSTANCE);

        // pollFirst(count)는 가장 점수가 낮은(오래된) N개의 원소를 Set에서 원자적으로 제거하고 반환
        Collection<Long> polledItems = queue.pollFirst(count);

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
        return keyGenerator.getWaitQueueKey(concertId);
    }
}