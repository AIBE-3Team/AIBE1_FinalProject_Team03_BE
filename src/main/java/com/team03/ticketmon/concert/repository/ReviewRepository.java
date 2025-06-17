package com.team03.ticketmon.concert.repository;

import com.team03.ticketmon.concert.domain.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

/*
 * Review Repository
 * 후기 데이터 접근 계층
 */

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
	List<Review> findByConcertConcertIdOrderByCreatedAtDesc(Long concertId);
}
