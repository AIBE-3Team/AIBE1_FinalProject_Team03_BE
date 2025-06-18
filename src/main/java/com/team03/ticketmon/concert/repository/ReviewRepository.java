package com.team03.ticketmon.concert.repository;

import com.team03.ticketmon.concert.domain.Review;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

/*
 * Review Repository
 * 후기 데이터 접근 계층
 */

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
	Page<Review> findByConcertConcertIdOrderByCreatedAtDesc(Long concertId, Pageable pageable);
	List<Review> findByConcertConcertIdOrderByCreatedAtDesc(Long concertId);
	boolean existsByIdAndConcertConcertId(Long id, Long concertId);
	Optional<Review> findByIdAndConcertConcertId(Long id, Long concertId);
	void deleteByIdAndConcertConcertId(Long id, Long concertId);
}
