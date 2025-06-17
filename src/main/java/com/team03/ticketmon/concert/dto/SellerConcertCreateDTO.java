package com.team03.ticketmon.concert.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;

/*
 * Seller Concert Create DTO
 * 판매자용 콘서트 생성 전송 객체
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SellerConcertCreateDTO {
	private String title;
	private String artist;
	private String description;
	private String venueName;
	private String venueAddress;
	private LocalDate concertDate;
	private LocalTime startTime;
	private LocalTime endTime;
	private Integer totalSeats;
	private LocalDateTime bookingStartDate;
	private LocalDateTime bookingEndDate;
	private Integer minAge;
	private Integer maxTicketsPerUser;
	private String posterImageUrl;
}
