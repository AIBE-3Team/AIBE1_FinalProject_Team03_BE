package com.team03.ticketmon.queue.service;

import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;
import com.team03.ticketmon.queue.adapter.QueueRedisAdapter;
import com.team03.ticketmon.queue.dto.QueueStatusDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RScoredSortedSet;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Redis Sorted Set을 이용해 콘서트 대기열을 관리하는 서비스
 * 이 서비스는 대기열 추가, 순위 조회, 사용자 추출 등의 핵심 기능을 담당
 * 모든 연산은 원자성(Atomic)을 보장해야 합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WaitingQueueService {

    private final AdmissionService admissionService;
    private final QueueRedisAdapter queueRedisAdapter;

    /**
     * 특정 콘서트의 대기열에 사용자를 추가하고, 현재 대기 순번을 반환
     * 타임스탬프와 원자적 시퀀스를 조합한 유니크한 점수를 사용해 공정성을 보장
     *
     * @param concertId 대기열을 식별하는 콘서트 ID
     * @param userId    대기열에 추가할 사용자 ID
     * @return 1부터 시작하는 사용자의 대기 순번
     */
    public QueueStatusDto apply(Long concertId, Long userId) {
        RScoredSortedSet<Long> queue = queueRedisAdapter.getQueue(concertId);

        // 1. 원자적 슬롯 점유 시도
        if (queue.isEmpty() && admissionService.tryClaimSlot(concertId)) {
            log.debug("[userId: {}] 즉시 입장 처리 시작.", userId);
            String accessKey = admissionService.grantAccess(concertId, userId);
            return QueueStatusDto.immediateEntry(accessKey);
        }

        // 2. 슬롯이 꽉 찼거나, 점유 시도에 실패(경쟁에서 밀림)하면 대기열로 진입
        log.debug("사용자 {} 대기열 진입 처리 시작.", userId);
        long uniqueScore = queueRedisAdapter.generateQueueScore(concertId);

        if (!queue.addIfAbsent(uniqueScore, userId)) {
            log.warn("[userId: {}] 이미 대기열에 등록된 상태", userId);
            Integer existingRank = queue.rank(userId);
            if (existingRank != null) {
                return QueueStatusDto.waiting(existingRank.longValue() + 1);
            }
            throw new BusinessException(ErrorCode.QUEUE_ALREADY_JOINED);
        }

        log.debug("[userId: {}] 대기열 신규 신청. [콘서트: {}, 부여된 점수: {}]", userId, concertId, uniqueScore);
        Integer rankIndex = queue.rank(userId);

        if (rankIndex == null) {
            log.error("[userId: {}] 대기열 순위 조회 실패", userId);
            throw new BusinessException(ErrorCode.SERVER_ERROR);
        }

        return QueueStatusDto.waiting(rankIndex.longValue() + 1);
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
        RScoredSortedSet<Long> queue = queueRedisAdapter.getQueue(concertId);

        // 가장 오래된 N개의 원소를 Set에서 원자적으로 제거하고 반환
        Collection<Long> polledItems = queue.pollFirst(count);

        // Collection을 List로 변환하여 반환
        return new ArrayList<>(polledItems);
    }

    public QueueStatusDto getUserStatus(Long concertId, Long userId) {
        // 1. AccessKey가 이미 발급되었는지 먼저 확인
        RBucket<String> accessKeyBucket = queueRedisAdapter.getAccessKeyBucket(concertId, userId);
        String accessKey = accessKeyBucket.get();

        if (accessKey != null) {
            return QueueStatusDto.admitted(accessKey);
        }

        // 2. 대기열에 있는지 확인
        RScoredSortedSet<Long> queue = queueRedisAdapter.getQueue(concertId);
        Integer rank = queue.rank(userId);

        if (rank != null) {
            return QueueStatusDto.waiting(rank.longValue() + 1);
        }

        // 3. 둘 다 해당 없으면 에러 또는 이탈 상태 반환
        return QueueStatusDto.expiredOrNotInQueue();
    }

}