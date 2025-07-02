package com.team03.ticketmon._global.controller;

import com.team03.ticketmon._global.dto.UploadResponseDTO;
import com.team03.ticketmon._global.service.ExampleProfileImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/example")
public class ExampleProfileController {

    private final ExampleProfileImageService exampleProfileImageService;

    @PostMapping("/profile/image")
    public ResponseEntity<UploadResponseDTO> uploadProfileImage(@RequestParam("file") MultipartFile file) {
        // TODO: 현재는 userId를 임의로 1L 고정 (추후 로그인 사용자 ID로 대체 필요)
        return ResponseEntity.ok(exampleProfileImageService.uploadProfileImage("Example", file));
    }
}
