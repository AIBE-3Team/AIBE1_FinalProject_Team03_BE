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

	/**
	 * 예매 가능한 콘서트 조회
	 */
	@Query("SELECT c FROM Concert c WHERE " +
		"c.status = 'ON_SALE' AND " +
		"c.bookingStartDate <= CURRENT_TIMESTAMP AND " +
		"c.bookingEndDate >= CURRENT_TIMESTAMP " +
		"ORDER BY c.concertDate ASC")
	List<Concert> findBookableConcerts();
}

