package com.team03.ticketmon.concert.dto;

/*
 * Concert Search DTO
 * 콘서트 검색 조건 전송 객체
 */

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConcertSearchDTO {
	private String keyword;
}