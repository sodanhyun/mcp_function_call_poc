package com.example.gemini_report.tools;

import com.example.gemini_report.service.CompanyInfoService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.genai.types.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CompanyInfoToolExecutor implements ToolExecutor {
    public static final String GET_COMPANY_INFO = "get_company_info";
    private final CompanyInfoService companyInfoService;
    private final ObjectMapper objectMapper;

    @Override
    public String getToolName() {
        return GET_COMPANY_INFO;
    }

    @Override
    public FunctionDeclaration getFunctionDeclaration() {
        return FunctionDeclaration.builder()
                .name(GET_COMPANY_INFO)
                .description("우리 회사를 소개하는 데이터를 가져옵니다. 사용자의 질문과 가장 유사한 정보를 찾습니다.")
                .parameters(
                        Schema.builder()
                                .type(Type.Known.OBJECT)
                                .properties(ImmutableMap.of(
                                        "userQuery", Schema.builder()
                                                .type(Type.Known.STRING)
                                                .description("회사에 대해 궁금한 질문 내용 (예: '회사 설립 연도가 언제인가요?')")
                                                .build()
                                ))
                                .required(ImmutableList.of("userQuery"))
                                .build()
                )
                .build();
    }

    @Override
    public String execute(Map<String, Object> args) {
        String userQuery = (String) args.get("userQuery");

        Map<String, Object> result = companyInfoService.getCompanyInfo(userQuery);
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error converting result data to JSON", e);
        }
    }

    @Override
    public String getTemplatedPrompt(String originalPrompt) {
        return "";
    }

    @Override
    public Content getSystemInstruction() {
        return Content.fromParts(Part.fromText("너는 친절한 상담가야."));
    }
}
