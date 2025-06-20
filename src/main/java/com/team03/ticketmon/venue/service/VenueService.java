package com.team03.ticketmon.venue.service;

import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;
import com.team03.ticketmon.venue.domain.Venue;
import com.team03.ticketmon.venue.dto.VenueDTO;
import com.team03.ticketmon.venue.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 공연장 관련 비즈니스 로직을 처리하는 서비스 클래스
 * 현재 시스템에서는 주로 다른 서비스에서 공연장 정보를 조회하는 역할을 담당
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VenueService {

    private final VenueRepository venueRepository;


    /**
     * 전체 공연장 정보를 조회
     *
     * @return 조회된 Venue 리스트
     */
    public List<VenueDTO> getAllVenues() {
        return venueRepository.findAll().stream()
                .map(VenueDTO::new)
                .collect(Collectors.toList());
    }

    /**
     * ID를 이용해 공연장 정보를 조회
     *
     * @param venueId 조회할 공연장의 ID
     * @return 조회된 Venue 엔티티
     * @throws BusinessException 해당 ID의 공연장이 없을 경우
     */
    public VenueDTO getVenue(Long venueId) {
        Venue venue = venueRepository.findById(venueId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VENUE_NOT_FOUND));
        return new VenueDTO(venue);
    }
}