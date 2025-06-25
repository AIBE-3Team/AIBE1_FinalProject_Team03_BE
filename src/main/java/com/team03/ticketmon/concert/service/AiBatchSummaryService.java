package com.team03.ticketmon.concert.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team03.ticketmon._global.config.AiSummaryConditionProperties;
import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;
import com.team03.ticketmon.concert.domain.Concert;
import com.team03.ticketmon.concert.domain.Review;
import com.team03.ticketmon.concert.dto.AiBatchSummaryResultDTO;
import com.team03.ticketmon.concert.dto.ReviewChangeDetectionDTO;
import com.team03.ticketmon.concert.repository.ConcertRepository;
import com.team03.ticketmon.concert.repository.ReviewRepository;
import com.team03.ticketmon.concert.util.ReviewChecksumGenerator;

import lombok.extern.slf4j.Slf4j;

/**
 * 🤖 AI 배치 요약 처리 서비스
 *
 * 스케줄링을 통해 주기적으로 콘서트 리뷰들을 AI로 요약하는 배치 작업을 수행합니다.
 *
 * 팀 예외 처리 규칙 준수:
 * - BusinessException + ErrorCode 사용
 * - GlobalExceptionHandler와 연동
 * - 의미있는 에러 메시지 제공
 */
@Slf4j
@Service
public class AiBatchSummaryService {

	@Autowired
	private ConcertRepository concertRepository;

	@Autowired
	private ReviewRepository reviewRepository;

	@Autowired
	private AiSummaryUpdateConditionService conditionService;

	@Autowired
	private AiSummaryService aiSummaryService;

	@Autowired
	private ReviewChecksumGenerator checksumGenerator;

	@Autowired
	private AiSummaryConditionProperties conditionProperties;

	/**
	 * 🕒 매일 새벽 2시에 AI 배치 요약 처리 실행
	 *
	 * @return 배치 처리 결과 DTO
	 */
	@Scheduled(cron = "0 0 2 * * *")
	public AiBatchSummaryResultDTO processBatch() {
		log.info("AI 배치 요약 처리 시작");

		try {
			// 1단계: 사전 필터링 - 최소 리뷰 개수 이상인 콘서트들만 선별
			List<Concert> candidateConcerts = concertRepository.findConcertsWithMinimumReviews(
				conditionProperties.getMinReviewCount()
			);

			log.info("AI 배치 처리 대상 콘서트 수: {}", candidateConcerts.size());

			// 2단계: 후보군 정밀 검사 및 처리
			int successCount = 0;
			int failCount = 0;

			for (Concert concert : candidateConcerts) {
				try {
					// 2-1. 업데이트 필요성 체크
					ReviewChangeDetectionDTO detection = conditionService.checkNeedsUpdate(concert, conditionProperties);

					if (detection.getNeedsUpdate()) {
						// 2-2. AI 요약 처리 실행
						processConcertAiSummary(concert);
						successCount++;
						log.info("AI 요약 처리 성공: concertId={}", concert.getConcertId());
					} else {
						// 2-3. 처리 스킵 (조건 미충족)
						log.debug("AI 요약 처리 스킵: concertId={}, 이유={}",
							concert.getConcertId(), detection.getChangeReason());
					}

				} catch (BusinessException e) {
					// 비즈니스 예외는 예상된 상황으로 간주하고 실패 처리
					failCount++;
					handleAiSummaryFailure(concert, e);
					log.warn("AI 요약 처리 비즈니스 실패: concertId={}, 에러코드={}, 메시지={}",
						concert.getConcertId(), e.getErrorCode().getCode(), e.getMessage());

				} catch (Exception e) {
					// 예상치 못한 시스템 오류
					failCount++;
					handleAiSummaryFailure(concert, e);
					log.error("AI 요약 처리 시스템 실패: concertId={}, 오류={}",
						concert.getConcertId(), e.getMessage(), e);
				}
			}

			log.info("AI 배치 요약 처리 완료 - 전체: {}, 성공: {}, 실패: {}",
				candidateConcerts.size(), successCount, failCount);

			return new AiBatchSummaryResultDTO(
				candidateConcerts.size(), successCount, failCount, LocalDateTime.now());

		} catch (Exception e) {
			// 배치 프로세스 자체의 치명적 오류
			log.error("AI 배치 요약 처리 중 치명적 오류 발생", e);
			throw new BusinessException(ErrorCode.SERVER_ERROR,
				"AI 배치 요약 처리 중 시스템 오류가 발생했습니다.");
		}
	}

	/**
	 * 🎯 개별 콘서트 AI 요약 처리 메서드
	 *
	 * @param concert 요약을 생성할 콘서트
	 * @throws BusinessException 요약 생성 실패 시 (팀 규칙 준수)
	 */
	@Transactional
	public void processConcertAiSummary(Concert concert) {
		try {
			// 1단계: 유효한 리뷰들 조회
			List<Review> reviews = reviewRepository.findValidReviewsForAiSummary(concert.getConcertId());

			// 2단계: 리뷰 존재 여부 검증 (팀 규칙 준수)
			validateReviewsForSummary(reviews, concert.getConcertId());

			// 3단계: AI 요약 생성 (AiSummaryService에서 예외 처리)
			String aiSummary = aiSummaryService.generateSummary(reviews);

			// 4단계: Concert 엔티티 업데이트
			updateConcertWithAiSummary(concert, reviews, aiSummary);

			// 5단계: 데이터베이스 저장
			concertRepository.save(concert);

			log.info("콘서트 AI 요약 업데이트 완료: concertId={}, 리뷰수={}",
				concert.getConcertId(), reviews.size());

		} catch (BusinessException e) {
			// 이미 BusinessException인 경우 그대로 전파 (팀 규칙)
			throw e;
		} catch (Exception e) {
			// 예상치 못한 오류는 서버 에러로 래핑 (팀 규칙)
			log.error("콘서트 AI 요약 처리 중 예상치 못한 오류", e);
			throw new BusinessException(ErrorCode.SERVER_ERROR,
				"콘서트 AI 요약 처리 중 오류가 발생했습니다.");
		}
	}

	/**
	 * 🔍 AI 요약 생성을 위한 리뷰 데이터 검증 메서드
	 *
	 * @param reviews 검증할 리뷰 목록
	 * @param concertId 콘서트 ID (로깅용)
	 * @throws BusinessException 검증 실패 시
	 */
	private void validateReviewsForSummary(List<Review> reviews, Long concertId) {
		// null 체크
		if (reviews == null) {
			log.warn("콘서트 리뷰 목록이 null입니다. concertId={}", concertId);
			throw new BusinessException(ErrorCode.REVIEW_NOT_FOUND,
				"요약할 리뷰 데이터를 찾을 수 없습니다.");
		}

		// 빈 리스트 체크
		if (reviews.isEmpty()) {
			log.warn("콘서트에 유효한 리뷰가 없습니다. concertId={}", concertId);
			throw new BusinessException(ErrorCode.REVIEW_NOT_FOUND,
				"요약을 생성할 유효한 리뷰가 없습니다.");
		}

		// 리뷰 내용 유효성 체크
		long validReviews = reviews.stream()
			.filter(review -> review.getDescription() != null)
			.filter(review -> !review.getDescription().trim().isEmpty())
			.count();

		if (validReviews == 0) {
			log.warn("콘서트에 내용이 있는 리뷰가 없습니다. concertId={}, 전체리뷰수={}",
				concertId, reviews.size());
			throw new BusinessException(ErrorCode.INVALID_REVIEW_DATA,
				"내용이 포함된 유효한 리뷰가 없습니다.");
		}

		log.debug("리뷰 검증 완료 - concertId={}, 전체: {}개, 유효: {}개",
			concertId, reviews.size(), validReviews);
	}

	/**
	 * 🎨 Concert 엔티티의 AI 관련 필드들을 업데이트하는 메서드
	 *
	 * @param concert 업데이트할 콘서트
	 * @param reviews 요약에 사용된 리뷰들
	 * @param aiSummary 생성된 AI 요약
	 */
	private void updateConcertWithAiSummary(Concert concert, List<Review> reviews, String aiSummary) {
		LocalDateTime now = LocalDateTime.now();

		// AI 요약 관련 필드 업데이트
		concert.setAiSummary(aiSummary);
		concert.setAiSummaryGeneratedAt(now);
		concert.setAiSummaryReviewCount(reviews.size());
		concert.setAiSummaryReviewChecksum(checksumGenerator.generateChecksum(reviews));

		// 성공 시 실패 관련 필드 초기화 (재시도 카운터 리셋)
		concert.setAiSummaryRetryCount(0);
		concert.setAiSummaryLastFailedAt(null);

		log.debug("Concert AI 관련 필드 업데이트 완료: concertId={}", concert.getConcertId());
	}

	/**
	 * 🚨 AI 요약 실패 처리 메서드
	 *
	 * 실패 정보를 Concert 엔티티에 기록하여 향후 재시도 로직에서 활용할 수 있도록 합니다.
	 *
	 * @param concert 실패한 콘서트
	 * @param exception 발생한 예외
	 */
	private void handleAiSummaryFailure(Concert concert, Exception exception) {
		try {
			LocalDateTime now = LocalDateTime.now();

			// 실패 카운터 증가 (null safe)
			Integer currentRetryCount = concert.getAiSummaryRetryCount();
			int newRetryCount = (currentRetryCount != null ? currentRetryCount : 0) + 1;
			concert.setAiSummaryRetryCount(newRetryCount);

			// 실패 시간 기록
			concert.setAiSummaryLastFailedAt(now);

			// 데이터베이스에 실패 정보 저장
			concertRepository.save(concert);

			log.info("AI 요약 실패 정보 저장 완료: concertId={}, 재시도횟수={}, 실패시간={}",
				concert.getConcertId(), newRetryCount, now);

		} catch (Exception saveException) {
			// 실패 정보 저장마저 실패한 경우 (치명적 상황)
			log.error("AI 요약 실패 정보 저장 중 오류 발생: concertId={}",
				concert.getConcertId(), saveException);
		}
	}
}