package com.example.gemini_report.langchain.converters;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON 객체와 Java 컬렉션 간의 변환을 담당하는 유틸리티 클래스입니다.
 */
public class JsonConverter {

    /**
     * GSON JsonObject를 Map<String, Object>로 변환합니다.
     * @param jsonObject 변환할 JsonObject
     * @return 변환된 Map
     */
    public static Map<String, Object> jsonToMap(JsonObject jsonObject) {
        Map<String, Object> map = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
            map.put(entry.getKey(), jsonElementToObject(entry.getValue()));
        }
        return map;
    }

    /**
     * GSON JsonArray를 List<Object>로 변환합니다.
     * @param jsonArray 변환할 JsonArray
     * @return 변환된 List
     */
    public static List<Object> jsonToList(JsonArray jsonArray) {
        List<Object> list = new ArrayList<>();
        for (JsonElement element : jsonArray) {
            list.add(jsonElementToObject(element));
        }
        return list;
    }

    /**
     * GSON JsonElement를 적절한 Java 객체(Map, List, Primitive)로 변환합니다.
     * @param jsonElement 변환할 JsonElement
     * @return 변환된 Java 객체
     */
    private static Object jsonElementToObject(JsonElement jsonElement) {
        if (jsonElement.isJsonObject()) {
            return jsonToMap(jsonElement.getAsJsonObject());
        } else if (jsonElement.isJsonArray()) {
            return jsonToList(jsonElement.getAsJsonArray());
        } else if (jsonElement.isJsonPrimitive()) {
            JsonPrimitive primitive = jsonElement.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                return primitive.getAsBoolean();
            } else if (primitive.isNumber()) {
                return primitive.getAsNumber();
            } else {
                return primitive.getAsString();
            }
        } else if (jsonElement.isJsonNull()) {
            return null;
        } else {
            throw new IllegalArgumentException("Unsupported JsonElement type: " + jsonElement);
        }
    }
}
