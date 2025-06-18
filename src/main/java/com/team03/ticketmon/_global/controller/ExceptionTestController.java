package com.team03.ticketmon._global.controller;

import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/test")
public class ExceptionTestController {

	// 예외 발생 테스트용 API
	@GetMapping("/error")
	public ResponseEntity<Void> throwBusinessException() {
		throw new BusinessException(ErrorCode.SEAT_ALREADY_TAKEN);
	}
}