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
}
