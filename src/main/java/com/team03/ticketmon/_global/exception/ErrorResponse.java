package com.team03.ticketmon._global.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindingResult;

import java.util.List;
import java.util.stream.Collectors;

/**
 * ✅ ErrorResponse: 에러 응답 통일 클래스<br>
 * ------------------------------------------------<br>
 * 이 클래스는 모든 API 예외 응답의 형식을 통일하기 위해 사용됩니다.<br><br>
 *
 * 📌 기본 구조:<br>
 * - success : 항상 false (성공 응답은 SuccessResponse 사용)<br>
 * - status  : HTTP 상태 코드 (예: 400, 401, 500 등)<br>
 * - code    : 내부 비즈니스 에러 코드 (예: "A001", "B003")<br>
 * - message : 사용자에게 보여줄 메시지<br><br>
 *
 * ✅ 사용 예시:<br>
 * - ErrorCode 기반: ErrorResponse.of(ErrorCode.LOGIN_FAILED)<br>
 * - 커스텀 메시지: ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "서버에 문제가 발생했습니다.")
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL) // null이 아닌 필드만 JSON에 포함
public class ErrorResponse {

    private final boolean success = false;
    private final int status;
    private final String code;
    private final String message;
    private List<ValidationError> errors;

    /**
     * ErrorCode 기반 생성자<br>
     * - 대부분의 예외는 사전에 정의된 ErrorCode enum을 기반으로 생성
     *
     * @param errorCode ErrorCode enum 값
     */
    private ErrorResponse(ErrorCode errorCode) {
        this.status = errorCode.getStatus();
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
    }

    /**
     * 필드 유효성 검사 에러를 포함하는 생성자
     *
     * @param errorCode ErrorCode enum 값
     * @param errors    필드 에러 리스트
     */
    private ErrorResponse(ErrorCode errorCode, List<ValidationError> errors) {
        this.status = errorCode.getStatus();
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage(); // "유효하지 않은 입력값입니다" 와 같은 포괄적 메시지
        this.errors = errors; // 상세 필드 에러 정보
    }

    /**
     * HttpStatus와 직접 입력한 메시지를 기반으로 생성<br>
     * - 예상하지 못한 일반 예외 처리에 사용(커스텀)
     *
     * @param status  HttpStatus 값 (예: INTERNAL_SERVER_ERROR)
     * @param code    에러 코드 문자열 (보통 status.name() 사용)
     * @param message 클라이언트에게 보여줄 메시지
     */
    private ErrorResponse(int status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    /**
     * 정적 팩토리 메서드 (ErrorCode 기반) <br>
     * - 비즈니스 예외 응답용으로 주로 사용
     *
     * @param errorCode 사전 정의된 ErrorCode
     * @return ErrorResponse 인스턴스
     */
    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode);
    }

    /**
     * 정적 팩토리 메서드 (오버로딩 / 커스텀 메시지 기반)<br>
     * - 일반적인 Exception 대응용
     *
     * @param httpStatus HTTP 상태
     * @param message    사용자에게 전달할 메시지
     * @return ErrorResponse 인스턴스
     */
    public static ErrorResponse of(HttpStatus httpStatus, String message) {
        return new ErrorResponse(httpStatus.value(), httpStatus.name(), message);
    }

    /**
     * BindingResult로부터 상세 에러 정보를 담은 ErrorResponse를 생성하는 정적 팩토리 메서드
     *
     * @param errorCode     ErrorCode enum 값 (보통 INVALID_INPUT)
     * @param bindingResult @Valid 실패 시 전달되는 BindingResult
     * @return 상세 에러 정보가 포함된 ErrorResponse 인스턴스
     */
    public static ErrorResponse of(ErrorCode errorCode, BindingResult bindingResult) {
        return new ErrorResponse(errorCode, ValidationError.from(bindingResult));
    }

    /**
     * 필드 에러를 표현하는 내부 정적 클래스
     */
    @Getter
    public static class ValidationError {
        private final String field;
        private final String message;

        private ValidationError(String field, String message) {
            this.field = field;
            this.message = message;
        }

        /** BindingResult에서 필드 에러 목록을 추출하여 ValidationError 리스트로 변환 */
        private static List<ValidationError> from(BindingResult bindingResult) {
            return bindingResult.getFieldErrors().stream()
                    .map(error -> new ValidationError(
                            error.getField(),
                            error.getDefaultMessage()
                    ))
                    .collect(Collectors.toList());
        }
    }
}