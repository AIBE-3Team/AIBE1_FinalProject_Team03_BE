package com.team03.ticketmon.concert.repository;

import com.team03.ticketmon.concert.domain.Concert;
import com.team03.ticketmon.concert.domain.enums.ConcertStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/*
 * Concert Repository
 * 콘서트 데이터 접근 계층
 */

@Repository
public interface ConcertRepository extends JpaRepository<Concert, Long> {

	/**
	 * 키워드로 콘서트 검색
	 */
	@Query("SELECT c FROM Concert c WHERE " +
		"c.status IN ('SCHEDULED', 'ON_SALE') AND " +
		"(LOWER(c.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
		"LOWER(c.artist) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
		"LOWER(c.venueName) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
		"ORDER BY c.concertDate ASC")
	List<Concert> findByKeyword(@Param("keyword") String keyword);

	/**
	 * 날짜 범위로 콘서트 조회
	 */
	@Query("SELECT c FROM Concert c WHERE " +
		"c.status IN ('SCHEDULED', 'ON_SALE') AND " +
		"(:startDate IS NULL OR c.concertDate >= :startDate) AND " +
		"(:endDate IS NULL OR c.concertDate <= :endDate) " +
		"ORDER BY c.concertDate ASC")
	List<Concert> findByDateRange(@Param("startDate") LocalDate startDate,
		@Param("endDate") LocalDate endDate);

	/**
	 * 가격 범위로 콘서트 조회
	 */
	@Query("SELECT DISTINCT c FROM Concert c " +
		"JOIN c.concertSeats cs " +
		"WHERE c.status IN ('SCHEDULED', 'ON_SALE') AND " +
		"(:minPrice IS NULL OR cs.price >= :minPrice) AND " +
		"(:maxPrice IS NULL OR cs.price <= :maxPrice) " +
		"ORDER BY c.concertDate ASC")
	List<Concert> findByPriceRange(@Param("minPrice") BigDecimal minPrice,
		@Param("maxPrice") BigDecimal maxPrice);

	/**
	 * 날짜와 가격 범위로 콘서트 조회
	 */
	@Query("SELECT DISTINCT c FROM Concert c " +
		"JOIN c.concertSeats cs " +
		"WHERE c.status IN ('SCHEDULED', 'ON_SALE') AND " +
		"(:startDate IS NULL OR c.concertDate >= :startDate) AND " +
		"(:endDate IS NULL OR c.concertDate <= :endDate) AND " +
		"(:minPrice IS NULL OR cs.price >= :minPrice) AND " +
		"(:maxPrice IS NULL OR cs.price <= :maxPrice) " +
		"ORDER BY c.concertDate ASC")
	List<Concert> findByDateAndPriceRange(@Param("startDate") LocalDate startDate,
		@Param("endDate") LocalDate endDate,
		@Param("minPrice") BigDecimal minPrice,
		@Param("maxPrice") BigDecimal maxPrice);

	@EntityGraph(attributePaths = {"concertSeats"})
	Page<Concert> findByStatusOrderByConcertDateAsc(ConcertStatus status,
		Pageable pageable);

	List<Concert> findByStatusOrderByConcertDateAsc(ConcertStatus status);

	List<Concert> findByConcertDateAndStatusOrderByConcertDateAsc(LocalDate concertDate,
		ConcertStatus status);

	List<Concert> findByStatusInOrderByConcertDateAsc(List<ConcertStatus> statuses);

	@EntityGraph(attributePaths = {"concertSeats"})
	Page<Concert> findByStatusInOrderByConcertDateAsc(List<ConcertStatus> statuses, Pageable pageable);

	/**
	 * 예매 가능한 콘서트 조회
	 */
	@Query("SELECT c FROM Concert c WHERE " +
		"c.status = 'ON_SALE' AND " +
		"c.bookingStartDate <= CURRENT_TIMESTAMP AND " +
		"c.bookingEndDate >= CURRENT_TIMESTAMP " +
		"ORDER BY c.concertDate ASC")
	List<Concert> findBookableConcerts();

	/**
	 * 리뷰 변동성 기반 업데이트 대상 조회
	 */
	@Query("SELECT c FROM Concert c WHERE " +
		"(c.aiSummary IS NULL OR c.aiSummary = '') AND " +
		"(SELECT COUNT(r) FROM Review r WHERE r.concert = c AND r.description IS NOT NULL AND r.description != '') >= :minReviewCount")
	List<Concert> findConcertsNeedingInitialAiSummary(@Param("minReviewCount") Integer minReviewCount);

	@Query("SELECT c FROM Concert c WHERE " +
		"c.aiSummary IS NOT NULL AND c.aiSummary != '' AND " +
		"c.lastReviewModifiedAt > c.aiSummaryGeneratedAt AND " +
		"(SELECT COUNT(r) FROM Review r WHERE r.concert = c AND r.description IS NOT NULL AND r.description != '') >= :minReviewCount")
	List<Concert> findConcertsNeedingAiSummaryUpdate(@Param("minReviewCount") Integer minReviewCount);

	@Query("SELECT c FROM Concert c WHERE " +
		"c.aiSummary IS NOT NULL AND c.aiSummary != '' AND " +
		"c.aiSummaryGeneratedAt < :beforeTime AND " +
		"(SELECT COUNT(r) FROM Review r WHERE r.concert = c AND r.description IS NOT NULL AND r.description != '') >= :minReviewCount")
	List<Concert> findConcertsWithOutdatedSummary(@Param("beforeTime") LocalDateTime beforeTime,
		@Param("minReviewCount") Integer minReviewCount);

	/**
	 * 리뷰 수 변화가 큰 콘서트들
	 */
	@Query("SELECT c FROM Concert c WHERE " +
		"c.aiSummary IS NOT NULL AND " +
		"ABS((SELECT COUNT(r) FROM Review r WHERE r.concert = c AND r.description IS NOT NULL AND r.description != '') - c.aiSummaryReviewCount) >= :significantChange")
	List<Concert> findConcertsWithSignificantReviewCountChange(@Param("significantChange") Integer significantChange);

	/**
	 * 사전 필터링: 최소 리뷰 수 이상인 콘서트들 조회
	 */
	@Query("SELECT c FROM Concert c WHERE " +
		"(SELECT COUNT(r) FROM Review r WHERE r.concert = c " +
		"AND r.description IS NOT NULL AND TRIM(r.description) != '' " +
		"AND LENGTH(TRIM(r.description)) >= 10) >= :minReviewCount")
	List<Concert> findConcertsWithMinimumReviews(@Param("minReviewCount") Integer minReviewCount);
}

