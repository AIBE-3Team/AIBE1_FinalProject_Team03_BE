package com.team03.ticketmon.booking.repository;

import com.team03.ticketmon.booking.domain.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Ticket 엔티티에 대한 데이터 접근을 처리하는 Spring Data JPA 리포지토리.
 * 좌석 상태 캐시 초기화 시 예매 여부 확인 등 특정 조회를 위해 사용됩니다.
 */
@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

	/**
	 * 특정 ConcertSeat ID에 해당하는 티켓이 존재하는지 확인합니다.
	 *
	 * @param concertSeatId 확인할 콘서트 좌석 ID
	 * @return 티켓 존재 시 true, 그렇지 않으면 false
	 */
	boolean existsByConcertSeat_ConcertSeatId(Long concertSeatId);
}
