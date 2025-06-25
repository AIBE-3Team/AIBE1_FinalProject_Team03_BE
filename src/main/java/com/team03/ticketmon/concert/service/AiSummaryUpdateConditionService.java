package com.team03.ticketmon.concert.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.team03.ticketmon._global.config.AiSummaryConditionProperties;
import com.team03.ticketmon.concert.domain.Concert;
import com.team03.ticketmon.concert.domain.Review;
import com.team03.ticketmon.concert.dto.ReviewChangeDetectionDTO;
import com.team03.ticketmon.concert.repository.ReviewRepository;
import com.team03.ticketmon.concert.util.ReviewChecksumGenerator;

@Service // Spring의 Service 계층 Bean으로 등록
public class AiSummaryUpdateConditionService {

	// 리뷰 데이터를 조회하기 위한 Repository
	private final ReviewRepository reviewRepository;
	// 체크섬 생성을 위한 유틸리티 클래스
	private final ReviewChecksumGenerator checksumGenerator;

	// 생성자를 통한 의존성 주입
	public AiSummaryUpdateConditionService(ReviewRepository reviewRepository,
		ReviewChecksumGenerator checksumGenerator) {
		this.reviewRepository = reviewRepository;
		this.checksumGenerator = checksumGenerator;
	}

	/**
	 * 콘서트의 AI 요약이 업데이트가 필요한지 확인하는 메서드
	 * 여러 조건을 종합적으로 검토하여 업데이트 필요성을 판단
	 * @param concert 검사할 콘서트 객체
	 * @param condition 업데이트 조건을 담은 설정 객체
	 * @return 변경 감지 결과를 담은 DTO
	 */
	public ReviewChangeDetectionDTO checkNeedsUpdate(Concert concert, AiSummaryConditionProperties condition) {
		// 현재 유효한 리뷰 목록을 데이터베이스에서 조회
		List<Review> currentReviews = reviewRepository.findValidReviewsForAiSummary(concert.getConcertId());
		Integer currentCount = currentReviews.size();
		String currentChecksum = checksumGenerator.generateChecksum(currentReviews);

		// 업데이트 필요 여부를 저장할 변수
		boolean needsUpdate = false;
		// 업데이트가 필요한 이유를 저장할 변수
		String changeReason = "";

		// ===== 단순화된 조건 검사 =====
		if (concert.getAiSummary() == null || concert.getAiSummary().isEmpty()) {
			// AI 요약 없음 → 사전 필터링으로 이미 10개는 보장됨
			needsUpdate = true;
			changeReason = "INITIAL_CREATION";
		} else {
			// AI 요약 있음 → 변화 감지 조건들만 체크

			// 조건 1: 리뷰 수 변화 체크
			Integer lastSummaryCount = concert.getAiSummaryReviewCount();
			int countDifference = Math.abs(currentCount - (lastSummaryCount != null ? lastSummaryCount : 0));
			double changeRatio = lastSummaryCount != null && lastSummaryCount > 0
				? (double)countDifference / lastSummaryCount : 0;

			if (countDifference >= condition.getSignificantCountChange() ||
				changeRatio >= condition.getSignificantCountChangeRatio()) {
				needsUpdate = true;
				changeReason = "COUNT_CHANGED";
			}

			// 조건 2: 리뷰 내용 변화 체크
			else if (condition.getUpdateOnAnyContentChange() &&
				!currentChecksum.equals(concert.getAiSummaryReviewChecksum())) {
				needsUpdate = true;
				changeReason = "CONTENT_CHANGED";
			}

			// 조건 3: 시간 기반 업데이트
			else if (concert.getAiSummaryGeneratedAt() != null) {
				LocalDateTime updateThreshold = LocalDateTime.now()
					.minusHours(condition.getMaxUpdateIntervalHours());
				if (concert.getAiSummaryGeneratedAt().isBefore(updateThreshold)) {
					needsUpdate = true;
					changeReason = "TIME_BASED_UPDATE";
				}
			}

			// 최소 리뷰 수 체크는 사전 필터링으로 이미 보장됨!
		}

		return ReviewChangeDetectionDTO.builder()
			.concertId(concert.getConcertId())
			.currentReviewCount(currentCount)
			.lastSummaryReviewCount(concert.getAiSummaryReviewCount())
			.currentReviewChecksum(currentChecksum)
			.lastSummaryChecksum(concert.getAiSummaryReviewChecksum())
			.lastReviewModifiedAt(concert.getLastReviewModifiedAt())
			.aiSummaryGeneratedAt(concert.getAiSummaryGeneratedAt())
			.needsUpdate(needsUpdate)
			.changeReason(changeReason)
			.build();
	}
}