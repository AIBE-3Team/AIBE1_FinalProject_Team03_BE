package com.team03.ticketmon.concert.repository;

import com.team03.ticketmon.concert.domain.Concert;
import com.team03.ticketmon.concert.domain.enums.ConcertStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

/*
 * Seller Concert Repository
 * 판매자용 콘서트 데이터 접근 계층
 */

@Repository
public interface SellerConcertRepository extends JpaRepository<Concert, Long> {

	/**
	 * 판매자별 콘서트 조회 (페이징)
	 */
	@Query("SELECT c FROM Concert c WHERE " +
		"c.sellerId = :sellerId " +
		"ORDER BY c.createdAt DESC")
	Page<Concert> findBySellerIdOrderByCreatedAtDesc(@Param("sellerId") Long sellerId,
		Pageable pageable);

	/**
	 * 판매자별 상태별 콘서트 조회
	 */
	@Query("SELECT c FROM Concert c WHERE " +
		"c.sellerId = :sellerId AND " +
		"c.status = :status " +
		"ORDER BY c.concertDate ASC")
	List<Concert> findBySellerIdAndStatus(@Param("sellerId") Long sellerId,
		@Param("status") ConcertStatus status);

	/**
	 * 판매자 권한 확인
	 */
	@Query("SELECT COUNT(c) > 0 FROM Concert c WHERE " +
		"c.concertId = :concertId AND " +
		"c.sellerId = :sellerId")
	boolean existsByConcertIdAndSellerId(@Param("concertId") Long concertId,
		@Param("sellerId") Long sellerId);

	/**
	 * 판매자별 콘서트 수 조회
	 */
	@Query("SELECT COUNT(c) FROM Concert c WHERE c.sellerId = :sellerId")
	long countBySellerIdOrderByCreatedAtDesc(@Param("sellerId") Long sellerId);

	/**
	 * 포스터 이미지 업데이트
	 */
	@Modifying
	@Query("UPDATE Concert c SET " +
		"c.posterImageUrl = :posterImageUrl, " +
		"c.updatedAt = CURRENT_TIMESTAMP " +
		"WHERE c.concertId = :concertId AND " +
		"c.sellerId = :sellerId")
	int updatePosterImageUrl(@Param("concertId") Long concertId,
		@Param("sellerId") Long sellerId,
		@Param("posterImageUrl") String posterImageUrl);
}

