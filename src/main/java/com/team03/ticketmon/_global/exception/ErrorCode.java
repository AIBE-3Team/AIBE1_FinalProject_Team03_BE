package com.team03.ticketmon._global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * ✅ ErrorCode 정의
 * <p>
 * 이 Enum은 모든 비즈니스 및 시스템 예외 코드를 통합 관리합니다.
 * <br><br>
 * 각 항목은 다음 정보를 포함합니다:
 * <ul>
 *   <li>HTTP 상태 코드 (status)</li>
 *   <li>내부 에러 코드 (code) - 팀 표준 형식 (예: "C001")</li>
 *   <li>사용자에게 전달할 메시지 (message)</li>
 * </ul>
 * <br>
 * 예외 발생 시 {@link BusinessException}과 함께 사용되며,
 * {@link ErrorResponse}를 통해 클라이언트에게 표준화된 응답을 제공합니다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common (공통 예외)
    INVALID_INPUT(400, "C001", "유효하지 않은 입력값입니다."),
    RESOURCE_NOT_FOUND(404, "C002", "리소스를 찾을 수 없습니다."),
    SERVER_ERROR(500, "C003", "서버에 오류가 발생했습니다."),
    INVALID_PAGE_NUMBER(400, "C004", "페이지 번호는 0 이상이어야 합니다."), // 추가: 페이징 검증
    INVALID_PAGE_SIZE(400, "C005", "페이지 크기는 1-100 사이여야 합니다."), // 추가: 페이징 검증
    INVALID_PAGE_REQUEST(400, "C006", "페이징 요청 정보가 유효하지 않습니다."), // 추가: Pageable 검증
    REQUEST_PARAM_MISSING(400, "C007", "필수 요청 파라미터가 누락되었습니다."), // <--- 추가됨

    // Auth & User (인증 및 사용자 관련)
    LOGIN_FAILED(401, "A001", "아이디 또는 비밀번호가 일치하지 않습니다."),
    INVALID_TOKEN(401, "A002", "인증 토큰이 유효하지 않습니다."),
    ACCESS_DENIED(403, "A003", "해당 예매를 취소할 권한이 없습니다."),
    EMAIL_DUPLICATION(409, "A004", "이미 가입된 이메일입니다."),
    SELLER_APPLY_ONCE(400, "A005", "판매자 권한 신청은 한 번만 가능합니다."),
    AUTHENTICATION_REQUIRED(401, "A006", "인증이 필요한 요청입니다. 로그인이 필요합니다."), // <--- 추가됨
    USER_NOT_FOUND(404, "A007", "사용자를 찾을 수 없습니다."), // <--- 추가됨

    // Permission & Admin (권한 및 관리자)
    ADMIN_ACCESS_DENIED(403, "P001", "관리자만 접근할 수 있습니다."),

    // Venue (공연장)
    VENUE_NOT_FOUND(404, "V001", "공연장을 찾을 수 없습니다."),

    // Ticket & Concert (티켓 및 콘서트) - 확장됨
    SEARCH_BAD_REQUEST(400, "T001", "콘서트 검색어는 2글자 이상 입력해주세요."),
    INVALID_DATE_RANGE(400, "T002", "조회 가능한 날짜 범위를 벗어났습니다."),
    CONCERT_NOT_FOUND(404, "T003", "콘서트를 찾을 수 없습니다."), // 추가: 콘서트 특화 에러
    INVALID_SEARCH_KEYWORD(400, "T004", "검색 키워드를 입력해주세요."), // 추가: 검색 키워드 검증
    INVALID_DATE_ORDER(400, "T005", "시작일은 종료일보다 이전이어야 합니다."), // 추가: 날짜 순서 검증
    INVALID_PRICE_RANGE(400, "T006", "가격 범위가 유효하지 않습니다."), // 추가: 가격 범위 검증
    INVALID_CONCERT_ID(400, "T007", "유효하지 않은 콘서트 ID입니다."), // 추가: 콘서트 ID 검증
    CONCERT_DATE_REQUIRED(400, "T008", "조회할 날짜를 입력해주세요."), // 추가: 날짜 필수 입력
    SEARCH_CONDITION_REQUIRED(400, "T009", "검색 조건을 입력해주세요."), // 추가: 검색 조건 필수

    // Queue & Access (대기열 및 입장)
    QUEUE_ALREADY_JOINED(409, "Q001", "이미 대기열에 등록된 사용자입니다."),
    INVALID_ACCESS_KEY(403, "Q002", "유효하지 않은 접근 키입니다."),
    STILL_IN_QUEUE(403, "Q003", "아직 입장 순서가 아닙니다. 잠시만 더 기다려주세요."),
    QUEUE_TOO_MANY_REQUESTS(429, "Q004", "접속 시도가 너무 많습니다. 잠시 후 다시 시도해주세요."),

    // Booking & Seat (예매 및 좌석)
    BOOKING_NOT_AVAILABLE(403, "B001", "예매가 가능한 상태가 아닙니다."),
    QUEUE_REQUIRED(429, "B002", "현재 접속자가 많아 대기열에 등록되었습니다."),
    SEAT_ALREADY_TAKEN(409, "B003", "이미 선택된 좌석입니다."),
    LOCK_ACQUISITION_FAILED(503, "B004", "좌석 선점에 실패했습니다. (락 획득 실패)"),
    BOOKING_TIMEOUT(408, "B005", "좌석 선점 시간이 만료되었습니다."),
    SEAT_NOT_FOUND(404, "B006", "좌석을 찾을 수 없습니다."),
    INVALID_SEAT_SELECTION(400, "B007", "선택한 좌석 중 일부를 찾을 수 없습니다."),
    BOOKING_NOT_FOUND(404, "B008", "요청한 예매 정보를 찾을 수 없습니다."),
    ALREADY_CANCELED_BOOKING(409, "B009", "이미 취소 처리된 예매입니다."),
    CANNOT_CANCEL_COMPLETED_BOOKING(409, "B010", "이미 관람(사용)이 완료된 예매는 취소할 수 없습니다."),
    CANCELLATION_PERIOD_EXPIRED(403, "B011", "예매 취소 가능 기간이 지났습니다."),

    // Review & Expectation (후기 및 기대평) - 새롭게 추가된 도메인
    REVIEW_ALREADY_EXISTS(409, "R001", "이미 후기를 작성했습니다."), // 추가: 중복 후기 방지
    REVIEW_NOT_FOUND(404, "R002", "후기를 찾을 수 없습니다."), // 추가: 후기 조회 실패
    EXPECTATION_REVIEW_NOT_FOUND(404, "R003", "기대평을 찾을 수 없습니다."), // 추가: 기대평 조회 실패
    INVALID_REVIEW_DATA(400, "R004", "후기 작성 정보가 유효하지 않습니다."), // 추가: 후기 데이터 검증

    // Seller & Concert Management (판매자 및 콘서트 관리) - 새롭게 추가된 도메인
    INVALID_SELLER_ID(400, "S001", "유효하지 않은 판매자 ID입니다."), // 추가: 판매자 ID 검증
    SELLER_PERMISSION_DENIED(403, "S002", "해당 콘서트를 관리할 권한이 없습니다."), // 추가: 판매자 권한 검증
    CONCERT_CREATION_FAILED(400, "S003", "콘서트 생성 정보가 유효하지 않습니다."), // 추가: 콘서트 생성 검증
    CONCERT_UPDATE_FAILED(400, "S004", "콘서트 수정 정보가 유효하지 않습니다."), // 추가: 콘서트 수정 검증
    INVALID_POSTER_URL(400, "S005", "포스터 이미지 URL을 입력해주세요."), // 추가: 포스터 URL 검증

    // Payment (결제)
    PAYMENT_AMOUNT_MISMATCH(400, "M001", "주문 금액이 일치하지 않습니다."),
    PAYMENT_FAILED(402, "M002", "결제에 실패했습니다."),
    INVALID_BOOKING_STATUS_FOR_PAYMENT(409, "M003", "결제를 진행할 수 없는 예매 상태입니다."),

    // AI Service (AI 서비스 관련)
    AI_SERVICE_UNAVAILABLE(503, "AI001", "AI 서비스가 일시적으로 사용할 수 없습니다."),
    AI_RESPONSE_INVALID(502, "AI002", "AI 서비스 응답 형식이 올바르지 않습니다."),
    AI_REQUEST_INVALID(400, "AI003", "AI 서비스 요청이 올바르지 않습니다.");

    /**
     * HTTP 상태 코드 (예: 400, 404, 500 등)
     */
    private final int status;

    /**
     * 내부 비즈니스 에러 코드 (예: "C001", "A003")<br>
     * - 코드 체계는 접두어로 도메인 분류 가능
     */
    private final String code;

    /**
     * 사용자에게 전달할 에러 메시지
     */
    private final String message;
}