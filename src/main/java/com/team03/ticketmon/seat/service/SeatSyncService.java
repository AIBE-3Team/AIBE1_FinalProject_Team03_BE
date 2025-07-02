package com.team03.ticketmon.seat.service;

import com.team03.ticketmon.concert.domain.ConcertSeat;
import com.team03.ticketmon.concert.repository.ConcertSeatRepository;
import com.team03.ticketmon.seat.domain.SeatStatus;
import com.team03.ticketmon.seat.domain.SeatStatus.SeatStatusEnum;
import com.team03.ticketmon.seat.dto.AllConcertSyncResult;
import com.team03.ticketmon.seat.dto.SeatSyncAnalysis;
import com.team03.ticketmon.seat.dto.SeatSyncResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 좌석 상태 Redis-DB 동기화 서비스
 *
 * 목적: Redis 캐시와 실제 DB의 티켓 상태 동기화를 통한 데이터 일관성 보장
 *
 * 주요 기능:
 * - DB 기준으로 Redis 좌석 상태 동기화
 * - 불일치 데이터 감지 및 수정
 * - 동기화 결과 리포트 생성
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatSyncService {

    private final ConcertSeatRepository concertSeatRepository;
    private final SeatStatusService seatStatusService;
    private final SeatInfoHelper seatInfoHelper;
    private final SeatStatusEventPublisher eventPublisher;

    /**
     * 특정 콘서트의 Redis-DB 좌석 상태 동기화 수행
     *
     * @param concertId 동기화할 콘서트 ID
     * @return 동기화 결과 정보
     */
    @Transactional(readOnly = true)
    public SeatSyncResult syncConcertSeats(Long concertId) {
        log.info("좌석 상태 동기화 시작: concertId={}", concertId);

        LocalDateTime syncStartTime = LocalDateTime.now();

        try {
            // 1. DB에서 실제 좌석 상태 조회 (Ticket 존재 여부 기준)
            List<ConcertSeat> dbSeats = concertSeatRepository.findByConcertIdWithDetails(concertId);

            if (dbSeats.isEmpty()) {
                log.warn("콘서트 좌석 데이터가 존재하지 않음: concertId={}", concertId);
                return SeatSyncResult.createEmpty(concertId, syncStartTime);
            }

            // 2. Redis에서 현재 캐시된 좌석 상태 조회
            Map<Long, SeatStatus> redisSeats = seatStatusService.getAllSeatStatus(concertId);

            // 3. DB-Redis 상태 비교 및 불일치 감지
            SeatSyncAnalysis analysis = analyzeSeatDifferences(dbSeats, redisSeats);

            // 4. 불일치 데이터 수정 (DB 기준으로 Redis 업데이트)
            int fixedCount = fixInconsistentSeats(concertId, analysis);

            LocalDateTime syncEndTime = LocalDateTime.now();

            SeatSyncResult result = SeatSyncResult.builder()
                    .concertId(concertId)
                    .syncStartTime(syncStartTime)
                    .syncEndTime(syncEndTime)
                    .totalDbSeats(dbSeats.size())
                    .totalRedisSeats(redisSeats.size())
                    .inconsistentSeats(analysis.getInconsistentSeats().size())
                    .missingInRedis(analysis.getMissingInRedis().size())
                    .extraInRedis(analysis.getExtraInRedis().size())
                    .fixedSeats(fixedCount)
                    .success(true)
                    .build();

            log.info("좌석 상태 동기화 완료: {}", result.getSummary());
            return result;

        } catch (Exception e) {
            log.error("좌석 상태 동기화 중 오류 발생: concertId={}", concertId, e);

            return SeatSyncResult.builder()
                    .concertId(concertId)
                    .syncStartTime(syncStartTime)
                    .syncEndTime(LocalDateTime.now())
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    /**
     * DB와 Redis 좌석 상태 차이점 분석
     *
     * @param dbSeats DB에서 조회한 좌석 목록
     * @param redisSeats Redis에서 조회한 좌석 상태 맵
     * @return 분석 결과
     */
    private SeatSyncAnalysis analyzeSeatDifferences(List<ConcertSeat> dbSeats, Map<Long, SeatStatus> redisSeats) {
        SeatSyncAnalysis.SeatSyncAnalysisBuilder builder = SeatSyncAnalysis.builder();

        // DB 좌석 ID 집합
        Set<Long> dbSeatIds = dbSeats.stream()
                .map(cs -> cs.getSeat().getSeatId())
                .collect(Collectors.toSet());

        // Redis 좌석 ID 집합
        Set<Long> redisSeatIds = redisSeats.keySet();

        // 1. Redis에 없는 좌석 (DB에만 존재)
        Set<Long> missingInRedis = dbSeatIds.stream()
                .filter(seatId -> !redisSeatIds.contains(seatId))
                .collect(Collectors.toSet());

        // 2. DB에 없는 좌석 (Redis에만 존재)
        Set<Long> extraInRedis = redisSeatIds.stream()
                .filter(seatId -> !dbSeatIds.contains(seatId))
                .collect(Collectors.toSet());

        // 3. 상태가 불일치한 좌석
        Set<Long> inconsistentSeats = dbSeats.stream()
                .filter(dbSeat -> {
                    Long seatId = dbSeat.getSeat().getSeatId();
                    SeatStatus redisSeat = redisSeats.get(seatId);

                    if (redisSeat == null) return false; // 이미 missingInRedis에서 처리됨

                    // DB 기준 예매 상태
                    boolean isBookedInDb = dbSeat.getTicket() != null;
                    SeatStatusEnum dbStatus = isBookedInDb ? SeatStatusEnum.BOOKED : SeatStatusEnum.AVAILABLE;

                    // Redis와 DB 상태 비교 (RESERVED 상태는 임시이므로 제외)
                    return !redisSeat.getStatus().equals(dbStatus) &&
                            !redisSeat.getStatus().equals(SeatStatusEnum.RESERVED);
                })
                .map(cs -> cs.getSeat().getSeatId())
                .collect(Collectors.toSet());

        return builder
                .missingInRedis(missingInRedis)
                .extraInRedis(extraInRedis)
                .inconsistentSeats(inconsistentSeats)
                .build();
    }

    /**
     * 불일치 좌석 상태 수정 (DB 기준으로 Redis 업데이트)
     *
     * @param concertId 콘서트 ID
     * @param analysis 분석 결과
     * @return 수정된 좌석 수
     */
    private int fixInconsistentSeats(Long concertId, SeatSyncAnalysis analysis) {
        int fixedCount = 0;

        // 1. Redis에 누락된 좌석 추가
        for (Long seatId : analysis.getMissingInRedis()) {
            try {
                addMissingSeatToRedis(concertId, seatId);
                fixedCount++;
                log.debug("Redis에 누락된 좌석 추가: concertId={}, seatId={}", concertId, seatId);
            } catch (Exception e) {
                log.error("Redis 좌석 추가 실패: concertId={}, seatId={}", concertId, seatId, e);
            }
        }

        // 2. Redis에만 있는 불필요한 좌석 제거
        for (Long seatId : analysis.getExtraInRedis()) {
            try {
                removeExtraSeatFromRedis(concertId, seatId);
                fixedCount++;
                log.debug("Redis에서 불필요한 좌석 제거: concertId={}, seatId={}", concertId, seatId);
            } catch (Exception e) {
                log.error("Redis 좌석 제거 실패: concertId={}, seatId={}", concertId, seatId, e);
            }
        }

        // 3. 상태 불일치 좌석 수정
        for (Long seatId : analysis.getInconsistentSeats()) {
            try {
                fixInconsistentSeat(concertId, seatId);
                fixedCount++;
                log.debug("좌석 상태 불일치 수정: concertId={}, seatId={}", concertId, seatId);
            } catch (Exception e) {
                log.error("좌석 상태 수정 실패: concertId={}, seatId={}", concertId, seatId, e);
            }
        }

        return fixedCount;
    }

    /**
     * Redis에 누락된 좌석 추가
     */
    private void addMissingSeatToRedis(Long concertId, Long seatId) {
        // DB에서 좌석 정보 조회
        ConcertSeat concertSeat = concertSeatRepository.findByConcertIdAndSeatId(concertId, seatId)
                .orElseThrow(() -> new IllegalStateException("좌석을 찾을 수 없음: " + seatId));

        // 좌석 정보 생성
        String seatInfo = SeatInfoHelper.generateSeatInfoFromEntity(concertSeat.getSeat());

        // 예매 상태 확인
        boolean isBooked = concertSeat.getTicket() != null;
        SeatStatusEnum status = isBooked ? SeatStatusEnum.BOOKED : SeatStatusEnum.AVAILABLE;

        // Redis에 좌석 상태 추가
        SeatStatus seatStatus = SeatStatus.builder()
                .id(concertId + "-" + seatId)
                .concertId(concertId)
                .seatId(seatId)
                .status(status)
                .userId(null)
                .reservedAt(null)
                .expiresAt(null)
                .seatInfo(seatInfo)
                .build();

        seatStatusService.updateSeatStatus(seatStatus);
    }

    /**
     * Redis에서 불필요한 좌석 제거
     */
    private void removeExtraSeatFromRedis(Long concertId, Long seatId) {
        // 현재는 SeatStatusService에 개별 좌석 삭제 메서드가 없으므로
        // AVAILABLE 상태로 업데이트하여 정리 (실제 삭제는 별도 구현 필요)
        SeatStatus cleanupStatus = SeatStatus.builder()
                .id(concertId + "-" + seatId)
                .concertId(concertId)
                .seatId(seatId)
                .status(SeatStatusEnum.AVAILABLE)
                .userId(null)
                .reservedAt(null)
                .expiresAt(null)
                .seatInfo("REMOVED")
                .build();

        seatStatusService.updateSeatStatus(cleanupStatus);

        log.info("불필요한 Redis 좌석 정리: concertId={}, seatId={}", concertId, seatId);
    }

    /**
     * 상태 불일치 좌석 수정 (DB 기준으로)
     */
    private void fixInconsistentSeat(Long concertId, Long seatId) {
        // DB에서 현재 상태 조회
        ConcertSeat concertSeat = concertSeatRepository.findByConcertIdAndSeatId(concertId, seatId)
                .orElseThrow(() -> new IllegalStateException("좌석을 찾을 수 없음: " + seatId));

        // DB 기준 상태 결정
        boolean isBooked = concertSeat.getTicket() != null;
        SeatStatusEnum correctStatus = isBooked ? SeatStatusEnum.BOOKED : SeatStatusEnum.AVAILABLE;

        // 좌석 정보 생성
        String seatInfo = SeatInfoHelper.generateSeatInfoFromEntity(concertSeat.getSeat());

        // Redis 상태 수정
        SeatStatus correctedStatus = SeatStatus.builder()
                .id(concertId + "-" + seatId)
                .concertId(concertId)
                .seatId(seatId)
                .status(correctStatus)
                .userId(null)
                .reservedAt(null)
                .expiresAt(null)
                .seatInfo(seatInfo)
                .build();

        seatStatusService.updateSeatStatus(correctedStatus);

        log.info("좌석 상태 수정 완료: concertId={}, seatId={}, status={}",
                concertId, seatId, correctStatus);
    }

    /**
     * 모든 활성 콘서트의 좌석 상태 동기화
     *
     * @return 전체 동기화 결과
     */
    @Transactional(readOnly = true)
    public AllConcertSyncResult syncAllActiveSeats() {
        log.info("전체 콘서트 좌석 상태 동기화 시작");

        LocalDateTime syncStartTime = LocalDateTime.now();
        // TODO: 활성 콘서트 목록 조회 로직 필요 (ConcertRepository 의존성 추가 후 구현)

        log.warn("전체 동기화 기능은 추후 구현 예정 (활성 콘서트 목록 조회 로직 필요)");

        return AllConcertSyncResult.builder()
                .syncStartTime(syncStartTime)
                .syncEndTime(LocalDateTime.now())
                .totalConcerts(0)
                .successCount(0)
                .failCount(0)
                .success(false)
                .message("구현 예정")
                .build();
    }
}