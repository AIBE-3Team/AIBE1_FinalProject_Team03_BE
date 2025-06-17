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
import java.time.LocalDateTime;
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
		validateCreateDTO(createDTO);

		Concert concert = convertToEntity(createDTO, sellerId);

		Concert savedConcert = sellerConcertRepository.save(concert);

		return convertToSellerDTO(savedConcert);
	}

	/**
	 * 콘서트 수정
	 */
	@Transactional
	public SellerConcertDTO updateConcert(Long sellerId, Long concertId, SellerConcertUpdateDTO updateDTO) {
		validateSellerId(sellerId);
		validateConcertId(concertId);
		validateUpdateDTO(updateDTO);

		if (!sellerConcertRepository.existsByConcertIdAndSellerId(concertId, sellerId)) {
			throw new BusinessException(ErrorCode.SELLER_PERMISSION_DENIED);
		}

		Concert concert = sellerConcertRepository.findById(concertId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CONCERT_NOT_FOUND));

		updateConcertEntity(concert, updateDTO);

		concert.setUpdatedAt(LocalDateTime.now());

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

		if (imageDTO == null || imageDTO.getPosterImageUrl() == null || imageDTO.getPosterImageUrl().trim().isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_POSTER_URL);
		}

		int updatedRows = sellerConcertRepository
			.updatePosterImageUrl(concertId, sellerId, imageDTO.getPosterImageUrl().trim());

		if (updatedRows == 0) {
			throw new BusinessException(ErrorCode.SELLER_PERMISSION_DENIED);
		}
	}

	/**
	 * 콘서트 삭제 (취소 처리)
	 */
	@Transactional
	public void deleteConcert(Long sellerId, Long concertId) {
		validateSellerId(sellerId);
		validateConcertId(concertId);

		Concert concert = sellerConcertRepository.findById(concertId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CONCERT_NOT_FOUND));

		if (!sellerConcertRepository.existsByConcertIdAndSellerId(concertId, sellerId)) {
			throw new BusinessException(ErrorCode.SELLER_PERMISSION_DENIED);
		}

		concert.setStatus(ConcertStatus.CANCELLED);
		concert.setUpdatedAt(LocalDateTime.now());

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
	 * 콘서트 생성 DTO 유효성 검증
	 */
	private void validateCreateDTO(SellerConcertCreateDTO createDTO) {
		if (createDTO == null) {
			throw new BusinessException(ErrorCode.CONCERT_CREATION_FAILED);
		}
		if (createDTO.getTitle() == null || createDTO.getTitle().trim().isEmpty()) {
			throw new BusinessException(ErrorCode.CONCERT_CREATION_FAILED);
		}
		if (createDTO.getArtist() == null || createDTO.getArtist().trim().isEmpty()) {
			throw new BusinessException(ErrorCode.CONCERT_CREATION_FAILED);
		}
		if (createDTO.getVenueName() == null || createDTO.getVenueName().trim().isEmpty()) {
			throw new BusinessException(ErrorCode.CONCERT_CREATION_FAILED);
		}
		if (createDTO.getConcertDate() == null) {
			throw new BusinessException(ErrorCode.CONCERT_CREATION_FAILED);
		}
		if (createDTO.getStartTime() == null || createDTO.getEndTime() == null) {
			throw new BusinessException(ErrorCode.CONCERT_CREATION_FAILED);
		}
		if (createDTO.getTotalSeats() == null || createDTO.getTotalSeats() <= 0) {
			throw new BusinessException(ErrorCode.CONCERT_CREATION_FAILED);
		}
		if (createDTO.getBookingStartDate() == null || createDTO.getBookingEndDate() == null) {
			throw new BusinessException(ErrorCode.CONCERT_CREATION_FAILED);
		}
	}

	/**
	 * 콘서트 수정 DTO 유효성 검증
	 */
	private void validateUpdateDTO(SellerConcertUpdateDTO updateDTO) {
		if (updateDTO == null) {
			throw new BusinessException(ErrorCode.CONCERT_UPDATE_FAILED);
		}
		if (updateDTO.getTitle() != null && updateDTO.getTitle().trim().isEmpty()) {
			throw new BusinessException(ErrorCode.CONCERT_UPDATE_FAILED);
		}
		if (updateDTO.getTotalSeats() != null && updateDTO.getTotalSeats() <= 0) {
			throw new BusinessException(ErrorCode.CONCERT_UPDATE_FAILED);
		}
	}

	/**
	 * 생성 DTO를 Entity로 변환
	 */
	private Concert convertToEntity(SellerConcertCreateDTO createDTO, Long sellerId) {
		Concert concert = new Concert();

		concert.setTitle(createDTO.getTitle().trim());
		concert.setArtist(createDTO.getArtist().trim());
		concert.setDescription(createDTO.getDescription());
		concert.setVenueName(createDTO.getVenueName().trim());
		concert.setVenueAddress(createDTO.getVenueAddress());
		concert.setConcertDate(createDTO.getConcertDate());
		concert.setStartTime(createDTO.getStartTime());
		concert.setEndTime(createDTO.getEndTime());
		concert.setTotalSeats(createDTO.getTotalSeats());
		concert.setBookingStartDate(createDTO.getBookingStartDate());
		concert.setBookingEndDate(createDTO.getBookingEndDate());
		concert.setMinAge(createDTO.getMinAge() != null ? createDTO.getMinAge() : 0);
		concert.setMaxTicketsPerUser(createDTO.getMaxTicketsPerUser() != null ? createDTO.getMaxTicketsPerUser() : 4);
		concert.setPosterImageUrl(createDTO.getPosterImageUrl());

		concert.setSellerId(sellerId);
		concert.setStatus(ConcertStatus.SCHEDULED);
		concert.setCreatedAt(LocalDateTime.now());
		concert.setUpdatedAt(LocalDateTime.now());

		return concert;
	}

	/**
	 * 수정 DTO로 Entity 업데이트
	 */
	private void updateConcertEntity(Concert concert, SellerConcertUpdateDTO updateDTO) {
		if (updateDTO.getTitle() != null) {
			concert.setTitle(updateDTO.getTitle().trim());
		}
		if (updateDTO.getArtist() != null) {
			concert.setArtist(updateDTO.getArtist().trim());
		}
		if (updateDTO.getDescription() != null) {
			concert.setDescription(updateDTO.getDescription());
		}
		if (updateDTO.getSellerId() != null) {
			concert.setSellerId(updateDTO.getSellerId());
		}
		if (updateDTO.getVenueName() != null) {
			concert.setVenueName(updateDTO.getVenueName().trim());
		}
		if (updateDTO.getVenueAddress() != null) {
			concert.setVenueAddress(updateDTO.getVenueAddress());
		}
		if (updateDTO.getConcertDate() != null) {
			concert.setConcertDate(updateDTO.getConcertDate());
		}
		if (updateDTO.getStartTime() != null) {
			concert.setStartTime(updateDTO.getStartTime());
		}
		if (updateDTO.getEndTime() != null) {
			concert.setEndTime(updateDTO.getEndTime());
		}
		if (updateDTO.getTotalSeats() != null) {
			concert.setTotalSeats(updateDTO.getTotalSeats());
		}
		if (updateDTO.getBookingStartDate() != null) {
			concert.setBookingStartDate(updateDTO.getBookingStartDate());
		}
		if (updateDTO.getBookingEndDate() != null) {
			concert.setBookingEndDate(updateDTO.getBookingEndDate());
		}
		if (updateDTO.getMinAge() != null) {
			concert.setMinAge(updateDTO.getMinAge());
		}
		if (updateDTO.getMaxTicketsPerUser() != null) {
			concert.setMaxTicketsPerUser(updateDTO.getMaxTicketsPerUser());
		}
		if (updateDTO.getStatus() != null) {
			concert.setStatus(updateDTO.getStatus());
		}
		if (updateDTO.getPosterImageUrl() != null) {
			concert.setPosterImageUrl(updateDTO.getPosterImageUrl());
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
}
