package com.team03.ticketmon.seat.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 좌석 관리 테스트 페이지 컨트롤러
 * - Thymeleaf 템플릿을 통한 좌석 테스트 페이지 제공
 * - 유저 플로우 관점의 테스트 환경 구성
 */
@Slf4j
@Controller
@RequestMapping("/seat")
@RequiredArgsConstructor
public class SeatTestController {

    /**
     * 좌석 관리 테스트 메인 페이지
     * - 좌석 선점/해제 테스트 기능 제공
     * - 실시간 폴링을 통한 좌석 상태 모니터링
     *
     * @param model Thymeleaf 모델 객체
     * @param concertId 테스트용 콘서트 ID (기본값: 1)
     * @param userId 테스트용 사용자 ID (기본값: 1)
     * @return 좌석 테스트 페이지 템플릿 경로
     */
    @GetMapping("/test")
    public String seatTestPage(Model model,
                               @RequestParam(defaultValue = "1") Long concertId,
                               @RequestParam(defaultValue = "1") Long userId) {

        log.info("좌석 테스트 페이지 접속: concertId={}, userId={}", concertId, userId);

        // 테스트 페이지에 필요한 기본 데이터 설정
        model.addAttribute("defaultConcertId", concertId);
        model.addAttribute("defaultUserId", userId);
        model.addAttribute("maxSeats", 150);
        model.addAttribute("seatsPerSection", 50);
        model.addAttribute("sections", new String[]{"A", "B", "C"});

        // API 엔드포인트 정보 제공
        model.addAttribute("apiBase", "/api/seats");

        return "seat/test";
    }

    /**
     * 좌석 관리 테스트 페이지 메인 (기본 경로)
     * - /seat/test로 리다이렉트
     *
     * @return 리다이렉트 경로
     */
    @GetMapping("")
    public String redirectToTest() {
        log.info("좌석 테스트 페이지 기본 경로 접속");
        return "redirect:/seat/test";
    }

    /**
     * 좌석 관리 도움말 페이지
     * - API 사용법 및 테스트 가이드 제공
     *
     * @param model Thymeleaf 모델 객체
     * @return 도움말 페이지 템플릿 경로
     */
    @GetMapping("/help")
    public String helpPage(Model model) {
        log.info("좌석 테스트 도움말 페이지 접속");

        // 도움말에 표시할 API 정보
        model.addAttribute("apiEndpoints", new String[]{
                "POST /api/seats/concerts/{concertId}/seats/{seatId}/reserve - 좌석 선점",
                "DELETE /api/seats/concerts/{concertId}/seats/{seatId}/release - 좌석 해제",
                "GET /api/seats/concerts/{concertId}/status - 전체 좌석 상태 조회",
                "GET /api/seats/concerts/{concertId}/seats/{seatId}/status - 개별 좌석 상태 조회"
        });

        return "seat/help";
    }
}