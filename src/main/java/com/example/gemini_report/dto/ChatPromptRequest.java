package com.example.gemini_report.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatPromptRequest {
    private String message;
    private String conversationId;
}
