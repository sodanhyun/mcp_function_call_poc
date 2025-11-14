package com.example.gemini_report.service;

import com.google.genai.Client;
import com.google.genai.types.EmbedContentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import javax.annotation.PostConstruct;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CompanyInfoService {
    private final Client client;
    private final Map<String, float[]> companyEmbeddings = new HashMap<>();
    private static final List<String> COMPANY_INFOS = Arrays.asList(
            "우리회사는 2011년에 설립된 스마트팩토리 전문기업입니다.",
            "주요 서비스는 MES, IoT 솔루션, AI 기반 생산관리입니다.",
            "본사는 창원시에 있으며, 국내외 20여개 고객사를 보유하고 있습니다.",
            "비전은 제조 현장의 혁신과 데이터 기반 생산성 향상입니다."
    );

    @PostConstruct
    public void init() {
        // Client와 Model을 컴포넌트 초기화 시 생성
        embedCompanyInfo();
    }

    public Map<String, Object> getCompanyInfo(String userQuery) {
        float[] userVec;
        Map<String, Object> result = new HashMap<>();

        if (userQuery == null || userQuery.trim().isEmpty()) {
            result.put("errorMessage", "{\"error\": \"userQuery is missing or empty.\"}");
            return result;
        }

        try {
            EmbedContentResponse userVecResp = this.client.models.embedContent("gemini-embedding-001", userQuery, null);
            userVec = extractEmbedding(userVecResp);
        } catch (Exception e) {
            result.put("errorMessage", "{\"error\": \"Failed to process user query.\"}");
            return result;
        }

        if (userVec.length == 0) {
            result.put("errorMessage", "{\"error\": \"Could not generate embedding for the user query.\"}");
            return result;
        }

        String bestMatch = "관련 정보를 찾을 수 없습니다.";
        double bestScore = -1.0;

        for (Map.Entry<String, float[]> entry : companyEmbeddings.entrySet()) {
            double score = cosineSimilarity(userVec, entry.getValue());
            if (score > bestScore) {
                bestScore = score;
                bestMatch = entry.getKey();
            }
        }

        result.put("사용자_질문", userQuery);
        result.put("가장_유사한_정보", bestMatch);
        result.put("유사도_점수", bestScore);
        return result;
    }


    private void embedCompanyInfo() {
        System.out.println("Generating company info embeddings...");
        for (String info : COMPANY_INFOS) {
            try {
                EmbedContentResponse resp = this.client.models.embedContent("gemini-embedding-001", info, null);
                float[] embedding = extractEmbedding(resp);
                companyEmbeddings.put(info, embedding);
            } catch (Exception e) {
                System.err.println("Failed to create embedding for: " + info);
                e.printStackTrace();
            }
        }
        System.out.println("Company info embeddings are ready.");
    }

    private float[] extractEmbedding(EmbedContentResponse response) {
        return Optional.ofNullable(response)
                .flatMap(EmbedContentResponse::embeddings)
                .flatMap(embeddings -> embeddings.stream().findFirst())
                .flatMap(embedding -> embedding.values().map(list -> {
                    float[] temp = new float[list.size()];
                    for (int i = 0; i < list.size(); i++) {
                        temp[i] = list.get(i);
                    }
                    return temp;
                }))
                .orElse(new float[0]);
    }

    private double cosineSimilarity(float[] vec1, float[] vec2) {
        if (vec1 == null || vec2 == null || vec1.length == 0 || vec2.length == 0 || vec1.length != vec2.length) {
            return 0.0;
        }
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < vec1.length; i++) {
            dot += vec1[i] * vec2[i];
            normA += vec1[i] * vec1[i];
            normB += vec2[i] * vec2[i];
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

}
