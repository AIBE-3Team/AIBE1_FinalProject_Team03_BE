package com.team03.ticketmon.seat.service;

import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;
import com.team03.ticketmon.concert.domain.ConcertSeat;
import com.team03.ticketmon.concert.repository.ConcertSeatRepository;
import com.team03.ticketmon.venue.domain.Seat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 좌석 정보 헬퍼 서비스
 * ✅ 수정사항:
 * - ConcertSeatId 기반 조회 메서드 추가
 * - 실제 DB 조회 기능 완전 구현
 * - 성능 최적화: 캐시 적용
 * - 더미 데이터 폴백 지원 (하위 호환성)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeatInfoHelper {

    private final ConcertSeatRepository concertSeatRepository;

    /**
     * ✅ 새로운 메서드: ConcertSeat ID 기반 좌석 정보 조회
     * 컨트롤러에서 ConcertSeat ID를 사용할 때 호출
     *
     * @param concertId 콘서트 ID
     * @param concertSeatId ConcertSeat ID
     * @return 좌석 정보 문자열 (예: "A-1-15" = 구역-열-번호)
     * @throws BusinessException 좌석을 찾을 수 없는 경우
     */
    @Cacheable(value = "seatInfoByConcertSeatId", key = "#concertId + ':' + #concertSeatId", unless = "#result == null")
    public String getSeatInfoByConcertSeatId(Long concertId, Long concertSeatId) {
        try {
            log.debug("ConcertSeat ID로 좌석 정보 조회: concertId={}, concertSeatId={}", concertId, concertSeatId);

            // ConcertSeat ID로 직접 조회
            Optional<ConcertSeat> concertSeatOpt = concertSeatRepository.findById(concertSeatId);

            if (concertSeatOpt.isEmpty()) {
                log.warn("ConcertSeat을 찾을 수 없음: concertSeatId={}", concertSeatId);
                throw new BusinessException(ErrorCode.SEAT_NOT_FOUND,
                        String.format("ConcertSeat을 찾을 수 없습니다. ConcertSeat ID: %d", concertSeatId));
            }

            ConcertSeat concertSeat = concertSeatOpt.get();

            // 콘서트 ID 일치 확인
            if (!concertSeat.getConcert().getConcertId().equals(concertId)) {
                log.warn("콘서트 ID 불일치: expected={}, actual={}, concertSeatId={}",
                        concertId, concertSeat.getConcert().getConcertId(), concertSeatId);
                throw new BusinessException(ErrorCode.SEAT_NOT_FOUND,
                        "해당 콘서트에 속하지 않는 좌석입니다.");
            }

            Seat seat = concertSeat.getSeat();

            // 좌석 정보 포맷: 구역-열-번호 (A-1-15)
            String seatInfo = String.format("%s-%s-%d",
                    seat.getSection() != null ? seat.getSection() : "?",
                    seat.getSeatRow() != null ? seat.getSeatRow() : "?",
                    seat.getSeatNumber() != null ? seat.getSeatNumber() : 0);

            log.debug("ConcertSeat ID 기반 좌석 정보 조회 성공: concertSeatId={}, seatInfo={}",
                    concertSeatId, seatInfo);

            return seatInfo;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("ConcertSeat ID 기반 좌석 정보 조회 중 오류: concertSeatId={}", concertSeatId, e);
            throw new BusinessException(ErrorCode.SERVER_ERROR,
                    "좌석 정보 조회 중 오류가 발생했습니다.");
        }
    }

    /**
     * 기존 메서드: Seat ID 기반 좌석 정보 조회 (하위 호환성 유지)
     *
     * @param concertId 콘서트 ID
     * @param seatId Seat ID
     * @return 좌석 정보 문자열
     * @throws BusinessException 좌석을 찾을 수 없는 경우
     */
    @Cacheable(value = "seatInfo", key = "#concertId + ':' + #seatId", unless = "#result == null")
    public String getSeatInfoFromDB(Long concertId, Long seatId) {
        try {
            log.debug("Seat ID로 좌석 정보 조회: concertId={}, seatId={}", concertId, seatId);

            // Seat ID로 ConcertSeat 조회
            Optional<ConcertSeat> concertSeatOpt = concertSeatRepository
                    .findByConcertIdAndSeatId(concertId, seatId);

            if (concertSeatOpt.isEmpty()) {
                log.warn("좌석 정보를 찾을 수 없음: concertId={}, seatId={}", concertId, seatId);
                throw new BusinessException(ErrorCode.SEAT_NOT_FOUND,
                        String.format("좌석을 찾을 수 없습니다. 콘서트ID: %d, 좌석ID: %d", concertId, seatId));
            }

            ConcertSeat concertSeat = concertSeatOpt.get();
            Seat seat = concertSeat.getSeat();

            String seatInfo = String.format("%s-%s-%d",
                    seat.getSection() != null ? seat.getSection() : "?",
                    seat.getSeatRow() != null ? seat.getSeatRow() : "?",
                    seat.getSeatNumber() != null ? seat.getSeatNumber() : 0);

            log.debug("Seat ID 기반 좌석 정보 조회 성공: concertId={}, seatId={}, seatInfo={}",
                    concertId, seatId, seatInfo);

            return seatInfo;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Seat ID 기반 좌석 정보 조회 중 오류: concertId={}, seatId={}", concertId, seatId, e);
            throw new BusinessException(ErrorCode.SERVER_ERROR,
                    "좌석 정보 조회 중 오류가 발생했습니다.");
        }
    }

    /**
     * 좌석 존재 여부만 빠르게 확인 (Seat ID 기반)
     */
    @Cacheable(value = "seatExists", key = "#concertId + ':' + #seatId")
    public boolean seatExists(Long concertId, Long seatId) {
        try {
            return concertSeatRepository.existsByConcertIdAndSeatId(concertId, seatId);
        } catch (Exception e) {
            log.error("좌석 존재 여부 확인 중 오류: concertId={}, seatId={}", concertId, seatId, e);
            return false;
        }
    }

    /**
     * ✅ 새로운 메서드: ConcertSeat 존재 여부 확인
     */
    @Cacheable(value = "concertSeatExists", key = "#concertId + ':' + #concertSeatId")
    public boolean concertSeatExists(Long concertId, Long concertSeatId) {
        try {
            return concertSeatRepository.existsByConcertIdAndConcertSeatId(concertId, concertSeatId);
        } catch (Exception e) {
            log.error("ConcertSeat 존재 여부 확인 중 오류: concertId={}, concertSeatId={}", concertId, concertSeatId, e);
            return false;
        }
    }

    /**
     * 더미 데이터 생성 (하위 호환성 + 폴백용)
     */
    public String generateDummySeatInfo(int seatNumber) {
        log.debug("더미 좌석 정보 생성: seatNumber={}", seatNumber);

        // 좌석 번호 유효성 검증
        if (seatNumber < 1 || seatNumber > 150) {
            log.error("유효하지 않은 좌석 번호: seatNumber={}", seatNumber);
            throw new IllegalArgumentException(
                    String.format("좌석 번호는 1부터 150 사이여야 합니다. 입력된 값: %d", seatNumber));
        }

        final int SEATS_PER_SECTION = 50;
        String section;
        int seatInSection;

        if (seatNumber <= SEATS_PER_SECTION) {
            section = "A";
            seatInSection = seatNumber;
        } else if (seatNumber <= SEATS_PER_SECTION * 2) {
            section = "B";
            seatInSection = seatNumber - SEATS_PER_SECTION;
        } else {
            section = "C";
            seatInSection = seatNumber - (SEATS_PER_SECTION * 2);
        }

        String seatInfo = String.format("%s-%d", section, seatInSection);
        log.debug("더미 좌석 정보 생성 완료: seatNumber={} -> seatInfo={}", seatNumber, seatInfo);

        return seatInfo;
    }

    /**
     * 정적 메서드: Seat 엔티티로부터 좌석 정보 생성
     * SeatCacheInitService에서 사용
     */
    public static String generateSeatInfoFromEntity(Seat seat) {
        if (seat == null) {
            log.warn("Seat 엔티티가 null입니다.");
            return "UNKNOWN";
        }

        String section = seat.getSection() != null ? seat.getSection() : "?";
        String seatRow = seat.getSeatRow() != null ? seat.getSeatRow() : "?";
        Integer seatNumber = seat.getSeatNumber() != null ? seat.getSeatNumber() : 0;

        return String.format("%s-%s-%d", section, seatRow, seatNumber);
    }
}