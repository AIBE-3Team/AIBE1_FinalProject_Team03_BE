package com.team03.ticketmon.seller_application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 판매자 권한 신청 요청 DTO
 * <p>
 * {@code POST /users/me/seller-requests} 요청 시 사용됩니다.
 * </p>
 * <p>
 * 이 DTO는 사용자가 판매자 권한을 신청할 때
 * 회사 정보, 담당자 정보, 그리고 (파일 정보를 제외한) 사업자 등록증과 같은 서류 파일을 담아 보냅니다.
 * </p>
 *
 * @version 1.0
 * @see com.team03.ticketmon.seller_application.domain.SellerApplication
 * @see com.team03.ticketmon.seller_application.service.SellerApplicationService
 */

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SellerApplicationRequestDTO {

    // 업체명: 필수 입력, 100자 이하
    @NotBlank(message = "업체명은 필수입니다.")
    @Size(max = 100, message = "업체명은 100자를 초과할 수 없습니다.")
    private String companyName;         // 업체명 (기회사명/거래처명 등)

    // 사업자등록번호: 필수 입력, 10자리 숫자 형식 (하이픈 제외)
    @NotBlank(message = "사업자등록번호는 필수입니다.")
    @Size(min = 10, max = 10, message = "사업자등록번호는 10자리여야 합니다.")
    @Pattern(regexp = "^[0-9]{10}$", message = "사업자등록번호는 10자리의 숫자여야 합니다.")
    private String businessNumber;      // 사업자등록번호 (하이픈 없이 10자리)

    // 담당자 이름: 필수 입력, 50자 이하
    @NotBlank(message = "담당자 이름은 필수입니다.")
    @Size(max = 50, message = "담당자 이름은 50자를 초과할 수 없습니다.")
    private String representativeName;  // 담당자(대표자)

    // 담당자 연락처: 필수 입력, 20자 이하, 한국 전화번호 형식 (숫자만)
    @NotBlank(message = "담당자 연락처는 필수입니다.")
    @Size(max = 20, message = "담당자 연락처는 20자를 초과할 수 없습니다.")
    // 대표적인 한국 전화번호 숫자만 (010으로 시작하는 휴대폰 또는 지역번호 포함 유선전화)
    // 실제 DB에는 varchar(20)이므로 이보다 긴 번호도 저장 가능하나, 유효성 검사에서 통상적인 한국 번호만 허용
    @Pattern(regexp = "^0\\d{1,2}\\d{3,4}\\d{4}$", message = "유효하지 않은 전화번호 형식입니다. 숫자만 입력해주세요. (예: 01012345678)")
    private String representativePhone; // 담당자(대표자) 연락처
}