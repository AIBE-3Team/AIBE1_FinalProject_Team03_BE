package com.team03.ticketmon._global.exception;

import com.team03.ticketmon._global.util.FileValidator;
import com.team03.ticketmon._global.util.uploader.supabase.SupabaseUploader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.stream.Collectors;

/**
 * ✅ GlobalExceptionHandler
 * <p>
 * 애플리케이션 전역에서 발생하는 예외를 한 곳에서 처리하는 클래스입니다.
 * <br><br>
 * 주요 처리 방식:
 * <ul>
 *   <li>{@link BusinessException} : 비즈니스 로직 중 발생하는 커스텀 예외</li>
 *   <li>{@link MethodArgumentNotValidException} : @Valid 검증 실패 예외</li> <!-- 🆕 추가됨 -->
 *   <li>{@link HttpMessageNotReadableException} : JSON 파싱 실패 예외</li> <!-- 🆕 추가됨 -->
 *   <li>{@link HttpRequestMethodNotSupportedException} : HTTP 메서드 불일치 예외</li> <!-- 🆕 추가됨 -->
 *   <li>{@link IllegalArgumentException} : 입력값 검증 예외</li>
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
     * 🆕 @Valid 검증 실패 예외 처리
     * <p>
     * @RequestBody @Valid ReviewDTO에서 검증 실패 시 발생하는 예외를 처리합니다.<br>
     * @NotBlank, @NotNull, @Min, @Max 등의 검증 어노테이션 실패를 400 Bad Request로 처리합니다.
     *
     * @param e MethodArgumentNotValidException (@Valid 검증 실패 예외)
     * @return 400 에러 응답 (ResponseEntity<ErrorResponse>)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        // 🎯 검증 실패 필드들의 에러 메시지 수집
        String errorMessage = e.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.joining(", "));

        // 🔥 @Valid 검증 실패를 INVALID_INPUT 에러 코드로 매핑
        ErrorResponse response = ErrorResponse.of(ErrorCode.INVALID_INPUT);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * 🆕 JSON 파싱 실패 예외 처리
     * <p>
     * @RequestBody로 전달된 JSON이 올바르지 않은 형식일 때 발생하는 예외를 처리합니다.<br>
     * 잘못된 JSON 구문, 타입 불일치 등을 400 Bad Request로 처리합니다.
     *
     * @param e HttpMessageNotReadableException (JSON 파싱 실패 예외)
     * @return 400 에러 응답 (ResponseEntity<ErrorResponse>)
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    protected ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        // 🔥 JSON 파싱 실패를 INVALID_INPUT 에러 코드로 매핑
        ErrorResponse response = ErrorResponse.of(ErrorCode.INVALID_INPUT);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * 🆕 HTTP 메서드 불일치 예외 처리
     * <p>
     * 지원하지 않는 HTTP 메서드로 요청했을 때 발생하는 예외를 처리합니다.<br>
     * 예: POST 엔드포인트에 GET 요청을 보낸 경우 405 Method Not Allowed로 처리합니다.
     *
     * @param e HttpRequestMethodNotSupportedException (HTTP 메서드 불일치 예외)
     * @return 405 에러 응답 (ResponseEntity<ErrorResponse>)
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    protected ResponseEntity<ErrorResponse> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        // 🔥 HTTP 메서드 불일치를 METHOD_NOT_ALLOWED 에러 코드로 매핑 (ErrorCode에 추가 필요)
        // 임시로 INVALID_INPUT 사용, 나중에 METHOD_NOT_ALLOWED 에러 코드 추가 권장
        ErrorResponse response = ErrorResponse.of(ErrorCode.INVALID_INPUT);
        return new ResponseEntity<>(response, HttpStatus.METHOD_NOT_ALLOWED);
    }

//    /**
//     * ✅ IllegalArgumentException 처리
//     * <p>
//     * Service 계층에서 발생하는 입력값 검증 예외를 처리합니다.<br>
//     * 대부분의 검증 실패는 400 Bad Request로 처리됩니다.
//     *
//     * @param e IllegalArgumentException (입력값 검증 실패 예외)
//     * @return 400 에러 응답 (ResponseEntity<ErrorResponse>)
//     */
//    @ExceptionHandler(IllegalArgumentException.class)
//    protected ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException e) {
//        // 🔥 IllegalArgumentException을 INVALID_INPUT 에러 코드로 매핑
//        ErrorResponse response = ErrorResponse.of(ErrorCode.INVALID_INPUT);
//        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
//    }

    /**
     * ✅ IllegalArgumentException 처리
     * <p>
     * Service 계층이나 유효성 검사 유틸리티(예: {@link FileValidator})에서 발생하는 입력값 검증 예외를 처리합니다.<br>
     * 파일 크기/형식 관련 예외를 포함하여 세분화된 에러 코드를 반환합니다.
     *
     * @param e IllegalArgumentException (입력값 검증 실패 예외)
     * @return 400 에러 응답 (ResponseEntity<ErrorResponse>)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    protected ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("IllegalArgumentException 발생: {}", e.getMessage());

        ErrorResponse response;
        String errorMessage = e.getMessage();

        if (errorMessage != null && errorMessage.contains("파일 크기는") && errorMessage.contains("초과할 수 없습니다.")) {
            // 파일 크기 제한 초과 (FileValidator에서 발생)
            response = ErrorResponse.of(ErrorCode.FILE_SIZE_LIMIT_EXCEEDED);
        } else if (errorMessage != null && errorMessage.contains("허용되지 않은 파일 형식입니다")) {
            // 허용되지 않는 파일 형식 (FileValidator에서 발생)
            response = ErrorResponse.of(ErrorCode.UNSUPPORTED_FILE_TYPE);
        } else {
            // 그 외의 모든 IllegalArgumentException (일반적인 유효하지 않은 입력값)
            // 수정: private 생성자 호출 대신 ErrorResponse.of(ErrorCode, String) 팩토리 메서드 사용
            response = ErrorResponse.of(ErrorCode.INVALID_INPUT, errorMessage);
        }

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * ✅ NullPointerException 처리
     * <p>
     * 예상치 못한 null 참조로 인한 예외를 처리합니다.<br>
     * 개발 단계에서 디버깅에 유용하며, 서버 내부 오류로 분류됩니다.
     *
     * @param e NullPointerException (null 참조 예외)
     * @return 500 에러 응답 (ResponseEntity<ErrorResponse>)
     */
    @ExceptionHandler(NullPointerException.class)
    protected ResponseEntity<ErrorResponse> handleNullPointerException(NullPointerException e) {
        // TODO: 로그 기록 추가 (개발 단계에서 디버깅용)
        ErrorResponse response = ErrorResponse.of(ErrorCode.SERVER_ERROR);
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // @RequestParam 필수 파라미터 누락 시 발생하는 예외를 처리
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestParameterException(MissingServletRequestParameterException ex) {
        log.error("필수 파라미터 누락: {}", ex.getParameterName());
        ErrorResponse response = ErrorResponse.of(ErrorCode.REQUEST_PARAM_MISSING);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * 🚨 타입 변환 예외 처리
     * sellerId="invalid-id" 또는 status="INVALID_STATUS" 등의 경우 발생
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    protected ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        ErrorResponse response = ErrorResponse.of(ErrorCode.INVALID_INPUT);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
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

    /**
     * ✅ 파일 업로드 중 발생하는 StorageUploadException을 처리하는 핸들러입니다.
     * <p>
     * 이 예외는 {@link SupabaseUploader}에서 래핑되어 던져지며, 파일 업로드 시스템 오류임을 명시적으로 나타냅니다.<br>
     * 클라이언트에게는 500 Internal Server Error와 함께 `FILE_UPLOAD_FAILED` 코드를 반환합니다.
     *
     * @param e StorageUploadException (파일 업로드 중 발생한 시스템 예외)
     * @return 500 에러 응답 (ResponseEntity<ErrorResponse>)
     */
    @ExceptionHandler(StorageUploadException.class)
    protected ResponseEntity<ErrorResponse> handleStorageUploadException(StorageUploadException e) {
        log.error("StorageUploadException 발생: {}", e.getMessage(), e);
        // 수정: ErrorResponse.of()를 호출
        ErrorResponse response = ErrorResponse.of(ErrorCode.FILE_UPLOAD_FAILED);
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * ✅ Spring 자체의 파일 업로드 크기 제한 초과 예외 처리
     * <p>
     * {@link org.springframework.web.multipart.MultipartResolver}에서 파일 크기 제한을 초과했을 때 발생합니다.<br>
     * 이는 애플리케이션의 {@code FileValidator}가 동작하기 전에 Spring 프레임워크 자체의 물리적 제한에 걸린 경우입니다.
     *
     * @param e MaxUploadSizeExceededException (최대 업로드 크기 초과 예외)
     * @return 400 Bad Request 에러 응답 (ResponseEntity<ErrorResponse>)
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    protected ResponseEntity<ErrorResponse> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        log.warn("MaxUploadSizeExceededException 발생: {}", e.getMessage(), e); // e 추가
        // 수정: ErrorResponse.of()를 호출
        ErrorResponse response = ErrorResponse.of(ErrorCode.FILE_SIZE_LIMIT_EXCEEDED);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }


}