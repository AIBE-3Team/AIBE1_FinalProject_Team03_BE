package com.team03.ticketmon.concert.service;

import com.team03.ticketmon.concert.dto.ConcertDTO;
import com.team03.ticketmon.concert.dto.ConcertFilterDTO;
import com.team03.ticketmon.concert.dto.ConcertSearchDTO;
import com.team03.ticketmon.concert.domain.Concert;
import com.team03.ticketmon.concert.domain.ConcertSeat;
import com.team03.ticketmon.concert.domain.enums.ConcertStatus;
import com.team03.ticketmon.concert.repository.ConcertRepository;
import com.team03.ticketmon.concert.repository.ConcertSeatRepository;
import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/*
 * Concert Service
 * 콘서트 비즈니스 로직 처리
 */

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConcertService {

	private final ConcertRepository concertRepository;
	private final ConcertSeatRepository concertSeatRepository;

	/**
	 * 전체 콘서트 조회
	 */
	public List<ConcertDTO> getAllConcerts() {
		List<ConcertStatus> activeStatuses = Arrays.asList(
			ConcertStatus.SCHEDULED,
			ConcertStatus.ON_SALE
		);

		return concertRepository
			.findByStatusInOrderByConcertDateAsc(activeStatuses)
			.stream()
			.map(this::convertToDTO)
			.collect(Collectors.toList());
	}

	/**
	 * 키워드로 콘서트 검색
	 */
	public List<ConcertDTO> searchByKeyword(String keyword) {
		if (keyword == null || keyword.trim().isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_SEARCH_KEYWORD);
		}

		return concertRepository
			.findByKeyword(keyword.trim())
			.stream()
			.map(this::convertToDTO)
			.collect(Collectors.toList());
	}

	/**
	 * 날짜 범위로 콘서트 필터링
	 */
	public List<ConcertDTO> filterByDateRange(LocalDate startDate, LocalDate endDate) {
		if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
			throw new BusinessException(ErrorCode.INVALID_DATE_ORDER);
		}

		return concertRepository
			.findByDateRange(startDate, endDate)
			.stream()
			.map(this::convertToDTO)
			.collect(Collectors.toList());
	}

	/**
	 * 가격 범위로 콘서트 필터링
	 */
	public List<ConcertDTO> filterByPriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
		if (minPrice != null && minPrice.compareTo(BigDecimal.ZERO) < 0) {
			throw new BusinessException(ErrorCode.INVALID_PRICE_RANGE);
		}
		if (maxPrice != null && maxPrice.compareTo(BigDecimal.ZERO) < 0) {
			throw new BusinessException(ErrorCode.INVALID_PRICE_RANGE);
		}
		if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
			throw new BusinessException(ErrorCode.INVALID_PRICE_RANGE);
		}

		return concertRepository
			.findByPriceRange(minPrice, maxPrice)
			.stream()
			.map(this::convertToDTO)
			.collect(Collectors.toList());
	}

	/**
	 * 날짜와 가격 범위로 콘서트 필터링
	 */
	public List<ConcertDTO> filterByDateAndPriceRange(
		LocalDate startDate, LocalDate endDate,
		BigDecimal minPrice, BigDecimal maxPrice) {

		if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
			throw new BusinessException(ErrorCode.INVALID_DATE_ORDER);
		}

		if (minPrice != null && minPrice.compareTo(BigDecimal.ZERO) < 0) {
			throw new BusinessException(ErrorCode.INVALID_PRICE_RANGE);
		}
		if (maxPrice != null && maxPrice.compareTo(BigDecimal.ZERO) < 0) {
			throw new BusinessException(ErrorCode.INVALID_PRICE_RANGE);
		}
		if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
			throw new BusinessException(ErrorCode.INVALID_PRICE_RANGE);
		}

		return concertRepository
			.findByDateAndPriceRange(startDate, endDate, minPrice, maxPrice)
			.stream()
			.map(this::convertToDTO)
			.collect(Collectors.toList());
	}

	/**
	 * 필터 조건 적용
	 */
	public List<ConcertDTO> applyFilters(ConcertFilterDTO filterDTO) {
		LocalDate startDate = filterDTO.getStartDate();
		LocalDate endDate = filterDTO.getEndDate();
		BigDecimal priceMin = filterDTO.getPriceMin();
		BigDecimal priceMax = filterDTO.getPriceMax();

		boolean hasDateFilter = startDate != null || endDate != null;
		boolean hasPriceFilter = priceMin != null || priceMax != null;

		if (hasDateFilter && hasPriceFilter) {
			return filterByDateAndPriceRange(startDate, endDate, priceMin, priceMax);
		} else if (hasDateFilter) {
			return filterByDateRange(startDate, endDate);
		} else if (hasPriceFilter) {
			return filterByPriceRange(priceMin, priceMax);
		} else {
			return getAllConcerts();
		}
	}

	/**
	 * 페이징으로 콘서트 조회
	 */
	public Page<ConcertDTO> getConcertsWithPaging(ConcertStatus status, Pageable pageable) {
		Page<Concert> concertPage = concertRepository
			.findByStatusOrderByConcertDateAsc(status, pageable);

		return concertPage.map(this::convertToDTO);
	}

	/**
	 * 날짜별 콘서트 조회
	 */
	public List<ConcertDTO> getConcertsByDate(LocalDate concertDate) {
		if (concertDate == null) {
			throw new BusinessException(ErrorCode.CONCERT_DATE_REQUIRED);
		}

		return concertRepository
			.findByConcertDateAndStatusOrderByConcertDateAsc(concertDate, ConcertStatus.ON_SALE)
			.stream()
			.map(this::convertToDTO)
			.collect(Collectors.toList());
	}

	/**
	 * 예매 가능한 콘서트 조회
	 */
	public List<ConcertDTO> getBookableConcerts() {
		return concertRepository
			.findBookableConcerts()
			.stream()
			.map(this::convertToDTO)
			.collect(Collectors.toList());
	}

	/**
	 * 콘서트 검색 (DTO 기반)
	 */
	public List<ConcertDTO> searchConcerts(ConcertSearchDTO searchDTO) {
		if (searchDTO == null || searchDTO.getKeyword() == null) {
			throw new BusinessException(ErrorCode.SEARCH_CONDITION_REQUIRED);
		}

		return searchByKeyword(searchDTO.getKeyword());
	}

	/**
	 * ID로 콘서트 조회
	 */
	public Optional<ConcertDTO> getConcertById(Long id) {
		if (id == null || id <= 0) {
			throw new BusinessException(ErrorCode.INVALID_CONCERT_ID);
		}

		return concertRepository.findById(id)
			.map(this::convertToDTO);
	}

	/**
	 * AI 요약 정보 조회
	 */
	public String getAiSummary(Long id) {
		if (id == null || id <= 0) {
			throw new BusinessException(ErrorCode.INVALID_CONCERT_ID);
		}

		return concertRepository.findById(id)
			.map(Concert::getAiSummary)
			.filter(summary -> summary != null && !summary.trim().isEmpty())
			.orElse("AI 요약 정보가 아직 생성되지 않았습니다.");
	}

	/**
	 * 예약 가능한 좌석 조회
	 */
	public List<ConcertSeat> getAvailableSeats(Long concertId) {
		if (concertId == null || concertId <= 0) {
			throw new BusinessException(ErrorCode.INVALID_CONCERT_ID);
		}

		if (!concertRepository.existsById(concertId)) {
			throw new BusinessException(ErrorCode.CONCERT_NOT_FOUND);
		}

		return concertSeatRepository.findAvailableSeatsByConcertId(concertId);
	}

	/**
	 * Entity를 DTO로 변환
	 */
	private ConcertDTO convertToDTO(Concert concert) {
		return new ConcertDTO(
			concert.getConcertId(),
			concert.getTitle(),
			concert.getArtist(),
			concert.getDescription(),
			concert.getSellerId(),
			concert.getVenueName(),
			concert.getVenueAddress(),
			concert.getConcertDate(),
			concert.getStartTime(),
			concert.getEndTime(),
			concert.getTotalSeats(),
			concert.getBookingStartDate(),
			concert.getBookingEndDate(),
			concert.getMinAge(),
			concert.getMaxTicketsPerUser(),
			concert.getStatus(),
			concert.getPosterImageUrl(),
			concert.getAiSummary()
		);
	}
}
