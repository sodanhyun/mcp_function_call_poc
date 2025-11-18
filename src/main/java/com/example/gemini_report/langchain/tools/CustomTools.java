package com.example.gemini_report.langchain.tools;

import com.example.gemini_report.auth.UserContextHolder;
import com.example.gemini_report.entity.CleaningData;
import com.example.gemini_report.service.CleaningDataService;
import com.google.gson.*;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * LangChain4j 에이전트가 사용할 수 있는 도구(Tool)들을 정의하는 클래스입니다.
 * 각 메서드는 @Tool 어노테이션을 통해 AI가 호출할 수 있는 함수로 등록됩니다.
 */
@Component
@RequiredArgsConstructor
public class CustomTools {

    private final CleaningDataService cleaningDataService;
    private final Gson gson;

    /**
     * 지정된 날짜 범위 내의 청소 데이터를 조회하여 요약 보고서를 생성합니다.
     * 이 메서드는 AI 에이전트에 의해 '날짜별 청소 보고서 조회'와 같은 자연어 요청이 있을 때 호출될 수 있습니다.
     *
     * @param startDate 조회 시작 날짜 (YYYY-MM-DD 형식)
     * @param endDate   조회 종료 날짜 (YYYY-MM-DD 형식)
     * @return 생성된 청소 데이터 요약 보고서 문자열 (JSON 형식)
     */
    @Tool("지정된 기간 동안의 청소 데이터를 가져옵니다.")
    public String getCleaningReport(String startDate, String endDate) {
        String currentUser = UserContextHolder.getUserName();
        System.out.println("Current user: " + currentUser);
        List<CleaningData> reportData = cleaningDataService.getCleaningReport(startDate, endDate);
        return gson.newBuilder()
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                .create().toJson(reportData);
    }

    public static class LocalDateTimeAdapter implements JsonSerializer<LocalDateTime>, JsonDeserializer<LocalDateTime> {
        private static final DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

        @Override
        public JsonElement serialize(LocalDateTime src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.format(formatter));
        }

        @Override
        public LocalDateTime deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            return LocalDateTime.parse(json.getAsString(), formatter);
        }
    }
}
