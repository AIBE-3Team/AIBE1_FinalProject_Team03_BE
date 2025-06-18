package com.team03.ticketmon._global.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * ✅ GlobalExceptionHandler
 * <p>
 * 애플리케이션 전역에서 발생하는 예외를 한 곳에서 처리하는 클래스입니다.
 * <br><br>
 * 주요 처리 방식:
 * <ul>
 *   <li>{@link BusinessException} : 비즈니스 로직 중 발생하는 커스텀 예외</li>
 *   <li>{@link IllegalArgumentException} : 입력값 검증 예외</li> <!-- 추가됨 -->
 *   <li>{@link Exception} : 예상치 못한 모든 예외 (Fallback)</li>
 * </ul>
 * <br>
 * 반환 형식은 모두 {@link ErrorResponse}를 사용하여 클라이언트에 통일된 구조로 전달됩니다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * ✅ 우리가 직접 정의한 비즈니스 예외 처리
     * <p>
     * ErrorCode 기반으로 구성된 {@link BusinessException}을 받아서<br>
     * HTTP 상태 코드와 메시지를 {@link ErrorResponse} 형태로 반환합니다.
     *
     * @param e BusinessException (비즈니스 로직 중 의도적으로 발생시킨 예외)
     * @return 통일된 에러 응답 (ResponseEntity<ErrorResponse>)
     */
    @ExceptionHandler(BusinessException.class)
    protected ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("BusinessException 발생: {}", e.getMessage()); // 비즈니스 예외 로그 기록 (WARN 레벨)
        ErrorResponse response = ErrorResponse.of(errorCode);
        return new ResponseEntity<>(response, HttpStatus.valueOf(errorCode.getStatus()));
    }

    /**
     * ✅ @Valid 애노테이션을 통한 유효성 검사 실패 시 발생하는 예외 처리
     * <p>
     * MethodArgumentNotValidException이 발생하면, 어떤 필드가 왜 유효성 검사에 실패했는지
     * 상세한 정보를 담은 {@link ErrorResponse}를 생성하여 반환합니다.
     *
     * @param e MethodArgumentNotValidException
     * @return 필드별 상세 오류 정보가 포함된 400 에러 응답
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        log.warn("MethodArgumentNotValidException 발생: {}", e.getMessage());
        ErrorResponse response = ErrorResponse.of(ErrorCode.INVALID_INPUT, e.getBindingResult());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * ✅ IllegalArgumentException 처리 (새롭게 추가됨)
     * <p>
     * Service 계층에서 발생하는 입력값 검증 예외를 처리합니다.<br>
     * 대부분의 검증 실패는 400 Bad Request로 처리됩니다.
     *
     * @param e IllegalArgumentException (입력값 검증 실패 예외)
     * @return 400 에러 응답 (ResponseEntity<ErrorResponse>)
     */
    @ExceptionHandler(IllegalArgumentException.class) // 추가: IllegalArgumentException 전용 핸들러
    protected ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException e) {
        // 🔥 추가: IllegalArgumentException을 INVALID_INPUT 에러 코드로 매핑
        ErrorResponse response = ErrorResponse.of(ErrorCode.INVALID_INPUT);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * ✅ NullPointerException 처리 (새롭게 추가됨)
     * <p>
     * 예상치 못한 null 참조로 인한 예외를 처리합니다.<br>
     * 개발 단계에서 디버깅에 유용하며, 서버 내부 오류로 분류됩니다.
     *
     * @param e NullPointerException (null 참조 예외)
     * @return 500 에러 응답 (ResponseEntity<ErrorResponse>)
     */
    @ExceptionHandler(NullPointerException.class) // 추가: NullPointerException 전용 핸들러
    protected ResponseEntity<ErrorResponse> handleNullPointerException(NullPointerException e) {
        // TODO: 로그 기록 추가 (개발 단계에서 디버깅용)
        // 추가: NPE를 서버 에러로 분류하여 처리
        ErrorResponse response = ErrorResponse.of(ErrorCode.SERVER_ERROR);
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * ✅ 시스템 예외 등 모든 기타 예외 처리 (최후의 보루)
     * <p>
     * 개발자가 명시적으로 처리하지 않은 모든 예외는 이 블록에서 처리됩니다.<br>
     * 서버 내부 오류(500)로 간주하고 에러 메시지를 포함한 {@link ErrorResponse}를 반환합니다.
     *
     * @param e 예상하지 못한 예외 (기타 모든 예외)
     * @return 500 에러 응답 (ResponseEntity<ErrorResponse>)
     */
    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("처리되지 않은 예외 발생!", e);
        ErrorResponse response = ErrorResponse.of(ErrorCode.SERVER_ERROR);
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}