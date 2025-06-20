package com.team03.ticketmon.venue.dto;

import com.team03.ticketmon.venue.domain.Venue;
import lombok.Getter;

/**
 * 공연장 정보 전송을 위한 DTO(Data Transfer Object)들을 담는 클래스
 */
@Getter
public class VenueDTO {
    private final Long venueId;
    private final String name;

    public VenueDTO(Venue venue) {
        this.venueId = venue.getVenueId();
        this.name = venue.getName();
    }
}