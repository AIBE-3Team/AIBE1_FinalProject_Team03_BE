package com.team03.ticketmon._global.exception;

import lombok.Getter;

/**
 * ✅ BusinessException: 커스텀 비즈니스 예외 처리 클래스<br>
 * ---------------------------------------------------------<br>
 * 서비스 로직에서 발생할 수 있는 도메인/비즈니스 예외 상황을 명확히 표현하기 위해 사용합니다.<br><br>
 *
 * ✅ 사용 배경:<br>
 * - 단순 Exception, RuntimeException은 의미 전달이 모호함<br>
 * - 비즈니스 흐름 제어를 위한 "의도된 예외"를 구분하기 위해 생성<br><br>
 *
 * ✅ 사용 방식:<br>
 * - throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);<br>
 * - GlobalExceptionHandler에서 자동으로 잡아 처리함<br><br>
 *
 * ✅ 특징:<br>
 * - ErrorCode enum을 기반으로 예외 메시지, HTTP 상태 등을 통일함<br>
 */
@Getter
public class BusinessException extends RuntimeException {

    /**
     * 사전에 정의된 ErrorCode(enum)로부터 예외 정보 구성
     */
    private final ErrorCode errorCode;

    /**
     * 커스텀 메시지 (ErrorCode의 기본 메시지를 재정의할 때 사용)
     */
    private final String customMessage;

    /**
     * BusinessException 생성자
     *
     * @param errorCode ErrorCode enum 값 (status, code, message 포함)
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());  // 예외 메시지는 ErrorCode에서 가져옴
        this.errorCode = errorCode;
		this.customMessage = null;
	}

    /**
     * 커스텀 메시지를 포함한 BusinessException 생성자
     *
     * @param errorCode ErrorCode enum 값 (status, code는 유지)
     * @param customMessage 구체적인 상황에 맞는 커스텀 메시지
     */
    public BusinessException(ErrorCode errorCode, String customMessage) {
        super(customMessage != null ? customMessage : errorCode.getMessage());
        this.errorCode = errorCode;
        this.customMessage = customMessage;
    }
}