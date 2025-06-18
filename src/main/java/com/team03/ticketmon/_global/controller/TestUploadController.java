package com.team03.ticketmon._global.controller;

import com.team03.ticketmon._global.dto.UploadResponseDTO;
import com.team03.ticketmon._global.service.TestUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ResponseEntity;

@Tag(name = "test-upload-controller", description = "Supabase 업로드 테스트용 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/test/upload")
public class TestUploadController {

    private final TestUploadService testUploadService;

    @Operation(summary = "파일 업로드 테스트", description = "multipart/form-data로 Supabase에 파일 업로드 테스트를 진행합니다.")
    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<UploadResponseDTO> uploadTest(
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(testUploadService.uploadTestFile(file));
    }
}
