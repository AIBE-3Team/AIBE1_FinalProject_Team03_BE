package com.team03.ticketmon._global.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UploadResponseDTO {
    private String fileName;
    private String fileUrl;
}
