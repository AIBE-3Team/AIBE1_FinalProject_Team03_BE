package com.team03.ticketmon.concert.service;

import com.team03.ticketmon._global.client.TogetherAiClient;
import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;
import com.team03.ticketmon.concert.domain.Review;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 🤖 AI를 활용한 리뷰 요약 서비스
 * 팀 예외 처리 규칙 준수:
 * - BusinessException + ErrorCode 사용
 * - GlobalExceptionHandler와 연동
 * - 의미있는 에러 메시지 제공
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiSummaryService {

	private final TogetherAiClient aiClient;

	// 상수 정의 - 매직 넘버 방지
	private static final int MAX_REVIEWS_FOR_SUMMARY = 100;
	private static final int MIN_SUMMARY_LENGTH = 50;
	private static final int MAX_SUMMARY_LENGTH = 3000;

	/**
	 * 🎯 리뷰 목록을 받아 AI 요약을 생성하는 메인 메서드
	 *
	 * @param reviews 요약할 리뷰 목록 (Review 엔티티 리스트)
	 * @return AI가 생성한 한국어 요약 내용
	 * @throws BusinessException 요약 생성 실패 시 (팀 규칙 준수)
	 */
	public String generateSummary(List<Review> reviews) {
		try {
			// 1단계: 입력값 검증 (팀 규칙 준수)
			validateReviews(reviews);

			log.info("AI 리뷰 요약 생성 시작 - 리뷰 개수: {}", reviews.size());

			// 2단계: AI 프롬프트 구성 (개선된 방식)
			String prompt = buildPrompt(reviews);

			// 3단계: AI API 호출 (TogetherAiClient 사용)
			String aiResponse = aiClient.sendChatRequest(prompt);

			// 4단계: AI 응답 검증 및 후처리
			String summary = parseAndValidateAiResponse(aiResponse);

			log.info("AI 리뷰 요약 생성 완료 - 요약 길이: {} 문자", summary.length());
			return summary;

		} catch (BusinessException e) {
			// 이미 BusinessException인 경우 그대로 전파 (팀 규칙)
			throw e;
		} catch (Exception e) {
			// 예상치 못한 오류는 서버 에러로 처리 (팀 규칙)
			log.error("AI 리뷰 요약 생성 중 예상치 못한 오류 발생", e);
			throw new BusinessException(ErrorCode.SERVER_ERROR,
				"리뷰 요약 생성 중 오류가 발생했습니다.");
		}
	}

	/**
	 * 🔍 입력 리뷰 데이터 검증 메서드
	 *
	 * @param reviews 검증할 리뷰 목록
	 * @throws BusinessException 검증 실패 시
	 */
	private void validateReviews(List<Review> reviews) {
		// null 체크
		if (reviews == null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
				"리뷰 데이터가 제공되지 않았습니다.");
		}

		// 빈 리스트 체크
		if (reviews.isEmpty()) {
			throw new BusinessException(ErrorCode.REVIEW_NOT_FOUND,
				"요약할 리뷰가 없습니다.");
		}

		// 리뷰 개수 제한 체크
		if (reviews.size() > MAX_REVIEWS_FOR_SUMMARY) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
				"한 번에 처리할 수 있는 리뷰는 최대 " + MAX_REVIEWS_FOR_SUMMARY + "개입니다.");
		}

		// 리뷰 내용 유효성 체크
		long validReviews = reviews.stream()
			.filter(review -> review.getDescription() != null)
			.filter(review -> !review.getDescription().trim().isEmpty())
			.count();

		if (validReviews == 0) {
			throw new BusinessException(ErrorCode.INVALID_REVIEW_DATA,
				"내용이 있는 리뷰가 없습니다.");
		}

		log.debug("리뷰 검증 완료 - 전체: {}개, 유효: {}개", reviews.size(), validReviews);
	}

	/**
	 * 🔧 개선된 AI 프롬프트 구성 메서드
	 * CSV 형식이 아닌 자연어 형식으로 구성하여 AI가 더 잘 이해할 수 있도록 개선
	 * @param reviews 요약할 리뷰 목록
	 * @return 완성된 AI 프롬프트
	 */
	private String buildPrompt(List<Review> reviews) {
		StringBuilder prompt = new StringBuilder();

		// 프롬프트 헤더: AI에게 작업 지시사항 명확히 전달
		prompt.append("다음은 콘서트 관람 후기들입니다. 이 후기들을 종합하여 요약해주세요.\n\n");

		// 리뷰 데이터를 자연어 형식으로 구성 (CSV 대신)
		for (int i = 0; i < reviews.size(); i++) {
			Review review = reviews.get(i);

			prompt.append("=== 후기 ").append(i + 1).append(" ===\n");

			// 제목 정보 포함 (기존 방식에서 누락되었던 부분)
			if (review.getTitle() != null && !review.getTitle().trim().isEmpty()) {
				prompt.append("제목: ").append(review.getTitle().trim()).append("\n");
			}

			// 평점 정보 포함 (기존 방식에서 누락되었던 부분)
			if (review.getRating() != null) {
				prompt.append("평점: ").append(review.getRating()).append("점/5점\n");
			}

			// 작성자 정보 포함
			if (review.getUserNickname() != null && !review.getUserNickname().trim().isEmpty()) {
				prompt.append("작성자: ").append(review.getUserNickname().trim()).append("\n");
			}

			// 후기 내용
			String content = review.getDescription();
			if (content != null && !content.trim().isEmpty()) {
				prompt.append("내용: ").append(content.trim()).append("\n");
			} else {
				prompt.append("내용: (내용 없음)\n");
			}

			prompt.append("\n");
		}

		// 프롬프트 푸터: AI에게 추가 지시사항
		prompt.append("\n위의 후기들을 바탕으로 콘서트에 대한 종합적인 요약을 작성해주세요.");

		log.debug("개선된 프롬프트 구성 완료 - 길이: {} 문자", prompt.length());
		return prompt.toString();
	}

	/**
	 * 🔍 AI 응답을 검증하고 후처리하는 메서드
	 *
	 * @param aiResponse AI로부터 받은 원본 응답
	 * @return 검증되고 후처리된 요약 텍스트
	 * @throws BusinessException 응답이 유효하지 않은 경우
	 */
	private String parseAndValidateAiResponse(String aiResponse) {
		// null 및 빈 응답 체크
		if (aiResponse == null || aiResponse.trim().isEmpty()) {
			throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID,
				"AI가 빈 응답을 반환했습니다. 다시 시도해주세요.");
		}

		String summary = aiResponse.trim();

		// 요약 길이 검증
		if (summary.length() < MIN_SUMMARY_LENGTH) {
			log.warn("AI 요약이 너무 짧습니다: {} 문자 (최소 {}자 권장)",
				summary.length(), MIN_SUMMARY_LENGTH);

			// 너무 짧으면 에러로 처리 (품질 관리)
			throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID,
				"생성된 요약이 너무 짧습니다. 다시 시도해주세요.");
		}

		// 요약 길이 제한 (너무 긴 경우 자동 잘라내기)
		if (summary.length() > MAX_SUMMARY_LENGTH) {
			log.warn("AI 요약이 너무 깁니다: {} 문자 -> {}자로 제한",
				summary.length(), MAX_SUMMARY_LENGTH);
			summary = summary.substring(0, MAX_SUMMARY_LENGTH) + "...";
		}

		// 추가 후처리: 불필요한 문자 제거 등
		summary = postProcessSummary(summary);

		return summary;
	}

	/**
	 * 🎨 요약 텍스트 후처리 메서드
	 *
	 * @param summary 원본 요약 텍스트
	 * @return 후처리된 요약 텍스트
	 */
	private String postProcessSummary(String summary) {
		// 연속된 공백 및 줄바꿈 정리
		summary = summary.replaceAll("\\s+", " ");
		summary = summary.replaceAll("\n{3,}", "\n\n");

		// 앞뒤 불필요한 문자 제거
		summary = summary.trim();

		return summary;
	}
}