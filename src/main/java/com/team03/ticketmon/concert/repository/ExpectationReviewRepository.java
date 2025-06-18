package com.team03.ticketmon.concert.repository;

import com.team03.ticketmon.concert.domain.ExpectationReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;

/*
 * Expectation Review Repository
 * 기대평 데이터 접근 계층
 */

@Repository
public interface ExpectationReviewRepository extends JpaRepository<ExpectationReview, Long> {
	Page<ExpectationReview> findByConcertConcertIdOrderByCreatedAtDesc(
	  Long concertId, Pageable pageable);
	@Query("SELECT er FROM ExpectationReview er WHERE er.id = :id AND er.concert.concertId = :concertId")
	Optional<ExpectationReview> findByIdAndConcertId(@Param("id") Long id, @Param("concertId") Long concertId);
}
