package com.team03.ticketmon.concert.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/*
 * Seller Concert Image Update DTO
 * 판매자용 콘서트 이미지 수정 전송 객체
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SellerConcertImageUpdateDTO {
	private String posterImageUrl;
}
