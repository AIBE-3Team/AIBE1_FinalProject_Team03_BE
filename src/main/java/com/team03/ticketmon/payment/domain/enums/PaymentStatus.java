package com.team03.ticketmon.payment.domain.enums; // 결제 도메인 하위에 생성

public enum PaymentStatus {
	PENDING,          // 결제 승인 대기
	DONE,             // 결제 완료
	CANCELED,         // 결제 취소 (전액)
	PARTIAL_CANCELED, // 부분 취소
	FAILED,         // 결제 실패
	EXPIRED;           // 결제 시간 만료

	// 최종 상태인지 확인하는 메서드
	public boolean isFinalState() {
		return this == DONE || this == CANCELED || this == FAILED || this == EXPIRED || this == PARTIAL_CANCELED;
	}
}
