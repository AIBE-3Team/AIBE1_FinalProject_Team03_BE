package com.team03.ticketmon.concert.repository;

import com.team03.ticketmon.concert.domain.ConcertSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

/**
 * Concert Seat Repository
 * 콘서트 좌석 데이터 접근 계층
 * ✅ 수정사항: ConcertSeat ID 기반 존재성 검증 메서드 추가
 */
@Repository
public interface ConcertSeatRepository extends JpaRepository<ConcertSeat, Long> {

	/**
	 * 예약 가능한 좌석 조회
	 */
	@Query("SELECT cs FROM ConcertSeat cs " +
			"LEFT JOIN cs.ticket t " +
			"WHERE cs.concert.concertId = :concertId " +
			"AND t.ticketId IS NULL " +
			"ORDER BY cs.seat.section, cs.seat.seatRow, cs.seat.seatNumber")
	List<ConcertSeat> findAvailableSeatsByConcertId(@Param("concertId") Long concertId);

	/**
	 * 기존 메서드: 특정 콘서트의 모든 좌석 조회 (Fetch Join 최적화)
	 */
	@Query("SELECT cs FROM ConcertSeat cs " +
			"JOIN FETCH cs.seat s " +
			"JOIN FETCH cs.concert c " +
			"LEFT JOIN FETCH cs.ticket t " +
			"WHERE cs.concert.concertId = :concertId " +
			"ORDER BY s.section, s.seatRow, s.seatNumber")
	List<ConcertSeat> findByConcertIdWithDetails(@Param("concertId") Long concertId);

	/**
	 * 특정 콘서트의 특정 좌석만 조회 (성능 최적화)
	 * SeatInfoHelper에서 사용
	 */
	@Query("SELECT cs FROM ConcertSeat cs " +
			"JOIN FETCH cs.seat s " +
			"JOIN FETCH cs.concert c " +
			"WHERE cs.concert.concertId = :concertId " +
			"AND cs.seat.seatId = :seatId")
	Optional<ConcertSeat> findByConcertIdAndSeatId(@Param("concertId") Long concertId,
												   @Param("seatId") Long seatId);

	/**
	 * 좌석 존재 여부만 빠르게 확인 (EXISTS 쿼리)
	 * SeatInfoHelper에서 사용
	 */
	@Query("SELECT CASE WHEN COUNT(cs) > 0 THEN true ELSE false END " +
			"FROM ConcertSeat cs " +
			"WHERE cs.concert.concertId = :concertId " +
			"AND cs.seat.seatId = :seatId")
	boolean existsByConcertIdAndSeatId(@Param("concertId") Long concertId,
									   @Param("seatId") Long seatId);

	/**
	 * ✅ 새로운 메서드: ConcertSeat ID 기반 존재성 검증
	 * 컨트롤러와 서비스에서 실제 좌석 존재 여부 확인용
	 */
	@Query("SELECT CASE WHEN COUNT(cs) > 0 THEN true ELSE false END " +
			"FROM ConcertSeat cs " +
			"WHERE cs.concert.concertId = :concertId " +
			"AND cs.concertSeatId = :concertSeatId")
	boolean existsByConcertIdAndConcertSeatId(@Param("concertId") Long concertId,
											  @Param("concertSeatId") Long concertSeatId);

	/**
	 * 특정 콘서트의 예매 가능한 좌석 수 조회
	 * 통계/모니터링용
	 */
	@Query("SELECT COUNT(cs) FROM ConcertSeat cs " +
			"WHERE cs.concert.concertId = :concertId " +
			"AND cs.ticket IS NULL")
	long countAvailableSeatsByConcertId(@Param("concertId") Long concertId);

	/**
	 * 특정 콘서트의 예매 완료된 좌석 수 조회
	 * 통계/모니터링용
	 */
	@Query("SELECT COUNT(cs) FROM ConcertSeat cs " +
			"WHERE cs.concert.concertId = :concertId " +
			"AND cs.ticket IS NOT NULL")
	long countBookedSeatsByConcertId(@Param("concertId") Long concertId);

	/**
	 * 구역별 좌석 조회 (구역별 배치도용)
	 * SeatLayoutService에서 사용
	 */
	@Query("SELECT cs FROM ConcertSeat cs " +
			"JOIN FETCH cs.seat s " +
			"JOIN FETCH cs.concert c " +
			"LEFT JOIN FETCH cs.ticket t " +
			"WHERE cs.concert.concertId = :concertId " +
			"AND s.section = :section " +
			"ORDER BY s.seatRow, s.seatNumber")
	List<ConcertSeat> findByConcertIdAndSection(@Param("concertId") Long concertId,
												@Param("section") String section);

	/**
	 * 특정 사용자의 예매 좌석 조회
	 * 사용자별 예매 내역 확인용
	 */
	@Query("SELECT cs FROM ConcertSeat cs " +
			"JOIN FETCH cs.seat s " +
			"JOIN FETCH cs.concert c " +
			"JOIN FETCH cs.ticket t " +
			"JOIN FETCH t.booking b " +
			"WHERE cs.concert.concertId = :concertId " +
			"AND b.userId = :userId")
	List<ConcertSeat> findByConcertIdAndUserId(@Param("concertId") Long concertId,
											   @Param("userId") Long userId);
}