package com.team03.ticketmon.concert.service;

import com.team03.ticketmon.concert.dto.*;
import com.team03.ticketmon.concert.domain.Concert;
import com.team03.ticketmon.concert.domain.enums.ConcertStatus;
import com.team03.ticketmon.concert.repository.SellerConcertRepository;
import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;

/*
 * Seller Concert Service
 * 판매자용 콘서트 비즈니스 로직 처리
 */

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerConcertService {

	private final SellerConcertRepository sellerConcertRepository;

	/**
	 * 판매자 콘서트 목록 조회 (페이징)
	 */
	public Page<SellerConcertDTO> getSellerConcerts(Long sellerId, Pageable pageable) {
		if (sellerId == null || sellerId <= 0) {
			throw new BusinessException(ErrorCode.INVALID_SELLER_ID);
		}

		Page<Concert> concertPage = sellerConcertRepository
			.findBySellerIdOrderByCreatedAtDesc(sellerId, pageable);

		return concertPage.map(this::convertToSellerDTO);
	}

	/**
	 * 판매자별 상태별 콘서트 조회
	 */
	public List<SellerConcertDTO> getSellerConcertsByStatus(Long sellerId, ConcertStatus status) {
		if (sellerId == null || sellerId <= 0) {
			throw new BusinessException(ErrorCode.INVALID_SELLER_ID);
		}
		if (status == null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}

		return sellerConcertRepository
			.findBySellerIdAndStatus(sellerId, status)
			.stream()
			.map(this::convertToSellerDTO)
			.collect(Collectors.toList());
	}

	/**
	 * 콘서트 생성
	 */
	@Transactional
	public SellerConcertDTO createConcert(Long sellerId, SellerConcertCreateDTO createDTO) {
		validateSellerId(sellerId);

		// DTO 유효성 추가 검증 (Controller 검증 보완)
		validateCreateDTO(createDTO);

		Concert concert = convertToEntity(createDTO, sellerId);
		Concert savedConcert = sellerConcertRepository.save(concert);

		return convertToSellerDTO(savedConcert);
	}

	private void validateCreateDTO(SellerConcertCreateDTO createDTO) {
		if (createDTO == null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "콘서트 정보가 없습니다");
		}

		// 필수 문자열 필드들 검증
		if (!hasValidStringValue(createDTO.getTitle())) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "콘서트 제목이 필요합니다");
		}
		if (!hasValidStringValue(createDTO.getArtist())) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "아티스트명이 필요합니다");
		}
		if (!hasValidStringValue(createDTO.getVenueName())) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "공연장명이 필요합니다");
		}

		// 필수 객체 필드들 검증
		if (createDTO.getConcertDate() == null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "콘서트 날짜가 필요합니다");
		}
		if (createDTO.getStartTime() == null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "시작 시간이 필요합니다");
		}
		if (createDTO.getEndTime() == null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "종료 시간이 필요합니다");
		}
		if (createDTO.getTotalSeats() == null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "총 좌석 수가 필요합니다");
		}
		if (createDTO.getBookingStartDate() == null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "예매 시작일시가 필요합니다");
		}
		if (createDTO.getBookingEndDate() == null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "예매 종료일시가 필요합니다");
		}
	}

	/**
	 * 콘서트 수정
	 */
	@Transactional
	public SellerConcertDTO updateConcert(Long sellerId, Long concertId, SellerConcertUpdateDTO updateDTO) {
		validateSellerId(sellerId);
		validateConcertId(concertId);

		// DTO 기본 검증
		if (updateDTO == null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "수정할 정보가 없습니다");
		}

		if (!sellerConcertRepository.existsByConcertIdAndSellerId(concertId, sellerId)) {
			throw new BusinessException(ErrorCode.SELLER_PERMISSION_DENIED);
		}

		Concert concert = sellerConcertRepository.findById(concertId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CONCERT_NOT_FOUND));

		updateConcertEntity(concert, updateDTO);

		Concert updatedConcert = sellerConcertRepository.save(concert);
		return convertToSellerDTO(updatedConcert);
	}

	/**
	 * 포스터 이미지 업데이트
	 */
	@Transactional
	public void updatePosterImage(Long sellerId, Long concertId, SellerConcertImageUpdateDTO imageDTO) {
		validateSellerId(sellerId);
		validateConcertId(concertId);

		// 더 엄격한 검증
		if (imageDTO == null) {
			throw new BusinessException(ErrorCode.INVALID_POSTER_URL, "이미지 정보가 없습니다");
		}

		String posterUrl = imageDTO.getPosterImageUrl();
		if (posterUrl == null) {
			throw new BusinessException(ErrorCode.INVALID_POSTER_URL, "포스터 URL이 없습니다");
		}

		String trimmedUrl = posterUrl.trim();
		if (trimmedUrl.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_POSTER_URL, "포스터 URL이 비어있습니다");
		}

		int updatedRows = sellerConcertRepository
			.updatePosterImageUrl(concertId, sellerId, trimmedUrl);

		if (updatedRows == 0) {
			throw new BusinessException(ErrorCode.SELLER_PERMISSION_DENIED);
		}
	}

	/**
	 * 콘서트 삭제 (취소 처리)
	 */
	@Transactional
	public void cancelConcert(Long sellerId, Long concertId) {
		validateSellerId(sellerId);
		validateConcertId(concertId);

		Concert concert = sellerConcertRepository.findById(concertId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CONCERT_NOT_FOUND));

		if (!sellerConcertRepository.existsByConcertIdAndSellerId(concertId, sellerId)) {
			throw new BusinessException(ErrorCode.SELLER_PERMISSION_DENIED);
		}

		concert.setStatus(ConcertStatus.CANCELLED);

		sellerConcertRepository.save(concert);
	}

	/**
	 * 판매자 콘서트 개수 조회
	 */
	public long getSellerConcertCount(Long sellerId) {
		validateSellerId(sellerId);
		return sellerConcertRepository.countBySellerIdOrderByCreatedAtDesc(sellerId);
	}

	/**
	 * 판매자 ID 유효성 검증
	 */
	private void validateSellerId(Long sellerId) {
		if (sellerId == null || sellerId <= 0) {
			throw new BusinessException(ErrorCode.INVALID_SELLER_ID);
		}
	}

	/**
	 * 콘서트 ID 유효성 검증
	 */
	private void validateConcertId(Long concertId) {
		if (concertId == null || concertId <= 0) {
			throw new BusinessException(ErrorCode.INVALID_CONCERT_ID);
		}
	}

	/**
	 * 생성 DTO를 Entity로 변환
	 */
	private Concert convertToEntity(SellerConcertCreateDTO createDTO, Long sellerId) {
		Concert concert = new Concert();

		// Null-safe 문자열 처리
		concert.setTitle(safeStringTrim(createDTO.getTitle()));
		concert.setArtist(safeStringTrim(createDTO.getArtist()));
		concert.setVenueName(safeStringTrim(createDTO.getVenueName()));

		// Optional 필드들 - null 허용
		concert.setDescription(safeOptionalStringTrim(createDTO.getDescription()));
		concert.setVenueAddress(safeOptionalStringTrim(createDTO.getVenueAddress()));
		concert.setPosterImageUrl(safeOptionalStringTrim(createDTO.getPosterImageUrl()));

		// 날짜/시간 필드들
		concert.setConcertDate(createDTO.getConcertDate());
		concert.setStartTime(createDTO.getStartTime());
		concert.setEndTime(createDTO.getEndTime());
		concert.setBookingStartDate(createDTO.getBookingStartDate());
		concert.setBookingEndDate(createDTO.getBookingEndDate());

		// 숫자 필드들 - 기본값 처리
		concert.setTotalSeats(createDTO.getTotalSeats());
		concert.setMinAge(createDTO.getMinAge() != null ? createDTO.getMinAge() : 0);
		concert.setMaxTicketsPerUser(createDTO.getMaxTicketsPerUser() != null ? createDTO.getMaxTicketsPerUser() : 4);

		// 시스템 설정값들
		concert.setSellerId(sellerId);
		concert.setStatus(ConcertStatus.SCHEDULED);

		return concert;
	}

	/**
	 * 수정 DTO로 Entity 업데이트
	 */
	private void updateConcertEntity(Concert concert, SellerConcertUpdateDTO updateDTO) {
		// 문자열 필드들 - null과 빈 문자열 모두 체크
		if (hasValidStringValue(updateDTO.getTitle())) {
			concert.setTitle(updateDTO.getTitle().trim());
		}
		if (hasValidStringValue(updateDTO.getArtist())) {
			concert.setArtist(updateDTO.getArtist().trim());
		}
		if (hasValidStringValue(updateDTO.getVenueName())) {
			concert.setVenueName(updateDTO.getVenueName().trim());
		}

		// Optional 문자열 필드들 - null 허용하지만 빈 문자열은 null로 변환
		if (updateDTO.getDescription() != null) {
			concert.setDescription(updateDTO.getDescription().trim().isEmpty() ? null : updateDTO.getDescription());
		}
		if (updateDTO.getVenueAddress() != null) {
			concert.setVenueAddress(updateDTO.getVenueAddress().trim().isEmpty() ? null : updateDTO.getVenueAddress());
		}
		if (updateDTO.getPosterImageUrl() != null) {
			concert.setPosterImageUrl(updateDTO.getPosterImageUrl().trim().isEmpty() ? null : updateDTO.getPosterImageUrl());
		}

		// 날짜/시간 필드들
		if (updateDTO.getConcertDate() != null) {
			concert.setConcertDate(updateDTO.getConcertDate());
		}
		if (updateDTO.getStartTime() != null) {
			concert.setStartTime(updateDTO.getStartTime());
		}
		if (updateDTO.getEndTime() != null) {
			concert.setEndTime(updateDTO.getEndTime());
		}
		if (updateDTO.getBookingStartDate() != null) {
			concert.setBookingStartDate(updateDTO.getBookingStartDate());
		}
		if (updateDTO.getBookingEndDate() != null) {
			concert.setBookingEndDate(updateDTO.getBookingEndDate());
		}

		// 숫자 필드들
		if (updateDTO.getTotalSeats() != null) {
			concert.setTotalSeats(updateDTO.getTotalSeats());
		}
		if (updateDTO.getMinAge() != null) {
			concert.setMinAge(updateDTO.getMinAge());
		}
		if (updateDTO.getMaxTicketsPerUser() != null) {
			concert.setMaxTicketsPerUser(updateDTO.getMaxTicketsPerUser());
		}

		// 상태 필드
		if (updateDTO.getStatus() != null) {
			concert.setStatus(updateDTO.getStatus());
		}
	}

	/**
	 * Entity를 판매자 DTO로 변환
	 */
	private SellerConcertDTO convertToSellerDTO(Concert concert) {
		return SellerConcertDTO.builder()
			.concertId(concert.getConcertId())
			.title(concert.getTitle())
			.artist(concert.getArtist())
			.description(concert.getDescription())
			.sellerId(concert.getSellerId())
			.venueName(concert.getVenueName())
			.venueAddress(concert.getVenueAddress())
			.concertDate(concert.getConcertDate())
			.startTime(concert.getStartTime())
			.endTime(concert.getEndTime())
			.totalSeats(concert.getTotalSeats())
			.bookingStartDate(concert.getBookingStartDate())
			.bookingEndDate(concert.getBookingEndDate())
			.minAge(concert.getMinAge())
			.maxTicketsPerUser(concert.getMaxTicketsPerUser())
			.status(concert.getStatus())
			.posterImageUrl(concert.getPosterImageUrl())
			.aiSummary(concert.getAiSummary())
			.createdAt(concert.getCreatedAt())
			.updatedAt(concert.getUpdatedAt())
			.build();
	}

	/**
	 * Optional 문자열 안전 trim 처리
	 */
	private String safeOptionalStringTrim(String str) {
		if (str == null) {
			return null;
		}
		String trimmed = str.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	/**
	 * 문자열 null-safe trim 처리
	 */
	private String safeStringTrim(String str) {
		if (str == null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "필수 문자열 필드가 null입니다");
		}
		return str.trim();
	}

	/**
	 * 유효한 문자열 값인지 확인 (null이 아니고 trim 후 비어있지 않음)
	 */
	private boolean hasValidStringValue(String str) {
		return str != null && !str.trim().isEmpty();
	}
}
