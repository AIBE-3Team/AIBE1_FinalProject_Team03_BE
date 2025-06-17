package com.team03.ticketmon.concert.repository;

import com.team03.ticketmon.concert.domain.ExpectationReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

/*
 * Expectation Review Repository
 * 기대평 데이터 접근 계층
 */

@Repository
public interface ExpectationReviewRepository extends JpaRepository<ExpectationReview, Long> {
	List<ExpectationReview> findByConcertConcertIdOrderByCreatedAtDesc(Long concertId);
}
