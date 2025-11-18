package com.example.gemini_report.dto;

import lombok.Data;

/**
 * 텍스트 임베딩 요청을 위한 DTO(Data Transfer Object)입니다.
 */
@Data
public class EmbeddingRequest {
    private String text;
}
