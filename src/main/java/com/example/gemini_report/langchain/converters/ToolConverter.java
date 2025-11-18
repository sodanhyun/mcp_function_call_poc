package com.example.gemini_report.langchain.converters;

import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.Schema;
import com.google.genai.types.Tool;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolParameters;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * LangChain4j의 ToolSpecification을 `com.google.genai`의 Tool 객체로 변환하는 유틸리티 클래스입니다.
 */
public class ToolConverter {

    /**
     * ToolSpecification 리스트를 Tool 리스트로 변환합니다.
     * @param toolSpecifications LangChain4j의 ToolSpecification 리스트
     * @return `com.google.genai`의 Tool 리스트
     */
    public static List<Tool> toGoogleAiTools(List<ToolSpecification> toolSpecifications) {
        if (toolSpecifications == null || toolSpecifications.isEmpty()) {
            return null;
        }
        return List.of(
                Tool.builder()
                        .functionDeclarations(toFunctionDeclarations(toolSpecifications))
                        .build()
        );
    }

    /**
     * ToolSpecification 리스트를 FunctionDeclaration 리스트로 변환합니다.
     * @param toolSpecifications LangChain4j의 ToolSpecification 리스트
     * @return `com.google.genai`의 FunctionDeclaration 리스트
     */
    private static List<FunctionDeclaration> toFunctionDeclarations(List<ToolSpecification> toolSpecifications) {
        return toolSpecifications.stream()
                .map(toolSpecification -> FunctionDeclaration.builder()
                        .name(toolSpecification.name())
                        .description(toolSpecification.description())
                        .parameters(toSchema(toolSpecification.parameters()))
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Tool의 파라미터 스키마를 `com.google.genai`의 Schema 객체로 변환합니다.
     * @param toolParameters LangChain4j의 Tool 파라미터 정의
     * @return `com.google.genai`의 Schema 객체
     */
    private static Schema toSchema(ToolParameters toolParameters) {
        if (toolParameters == null) {
            return null;
        }

        Schema.Builder builder = Schema.builder();
        builder.type(toolParameters.type().toUpperCase());

        if (toolParameters.properties() != null && !toolParameters.properties().isEmpty()) {
            Map<String, Schema> propertiesMap = new HashMap<>();
            for (Map.Entry<String, Map<String, Object>> entry : toolParameters.properties().entrySet()) {
                // Assuming properties are simple types or can be directly mapped to Schema
                // This part might need more sophisticated conversion if nested schemas are involved
                propertiesMap.put(entry.getKey(), mapToSchema(entry.getValue()));
            }
            builder.properties(propertiesMap);
        }

        if (toolParameters.required() != null && !toolParameters.required().isEmpty()) {
            builder.required(toolParameters.required());
        }
        
        return builder.build();
    }

    /**
     * Map 형태의 스키마를 `com.google.genai`의 Schema 객체로 재귀적으로 변환합니다.
     * @param map 스키마 정보를 담고 있는 Map
     * @return `com.google.genai`의 Schema 객체
     */
    @SuppressWarnings("unchecked")
    private static Schema mapToSchema(Map<String, Object> map) {
        Schema.Builder builder = Schema.builder();
        if (map.containsKey("type")) {
            builder.type(map.get("type").toString().toUpperCase());
        }
        if (map.containsKey("description")) {
            builder.description(map.get("description").toString());
        }
        if (map.containsKey("enum")) {
            builder.enum_(((List<String>) map.get("enum")));
        }
        if (map.containsKey("properties")) {
            Map<String, Schema> propertiesMap = new HashMap<>(); // New map for properties
            Map<String, Map<String, Object>> properties = (Map<String, Map<String, Object>>) map.get("properties");
            for (Map.Entry<String, Map<String, Object>> entry : properties.entrySet()) {
                propertiesMap.put(entry.getKey(), mapToSchema(entry.getValue()));
            }
            builder.properties(propertiesMap); // Set the properties map
        }
        if (map.containsKey("required")) {
            builder.required(((List<String>) map.get("required")));
        }
        if (map.containsKey("items")) {
            builder.items(mapToSchema((Map<String, Object>) map.get("items")));
        }
        return builder.build();
    }
}
