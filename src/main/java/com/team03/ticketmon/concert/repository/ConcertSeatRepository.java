package com.team03.ticketmon.concert.repository;

import com.team03.ticketmon.concert.domain.ConcertSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * Concert Seat Repository
 * 콘서트 좌석 데이터 접근 계층
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
	 * 특정 콘서트의 모든 좌석 조회 (예매 상태 포함) - ✨ 좌석 관리 기능에서 새로 추가
	 * Fetch Join을 사용하여 N+1 문제 방지
	 *
	 * @param concertId 콘서트 ID
	 * @return 콘서트의 모든 좌석 정보 (Seat, Ticket 포함)
	 */
	@Query("SELECT cs FROM ConcertSeat cs " +
			"LEFT JOIN FETCH cs.seat s " +
			"LEFT JOIN FETCH cs.ticket t " +
			"WHERE cs.concert.concertId = :concertId " +
			"ORDER BY s.section, s.seatRow, s.seatNumber")
	List<ConcertSeat> findByConcertIdWithDetails(@Param("concertId") Long concertId);
}
