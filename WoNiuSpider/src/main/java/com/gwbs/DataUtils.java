package com.gwbs;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public class DataUtils {
    // ===================== 工具方法 =====================
    public static Map<String, Object> tryParseObject(Object raw) {
        ObjectMapper MAPPER = new ObjectMapper();
        if (raw == null) {
            return null;
        }
        if (raw instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) raw;
            return m;
        }
        if (!(raw instanceof String)) return null;

        String s = (String) raw;
        try {
            return MAPPER.readValue(s, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ignore) {
            return null;
        }
    }

    public static String toJsonQuiet(Object obj) {
        ObjectMapper MAPPER = new ObjectMapper();
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    public static Map<String, Object> asMap(Object obj) {
        if (obj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) obj;
            return m;
        }
        return null;
    }

    public static Integer toInt(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Integer) return (Integer) obj;
        if (obj instanceof Long) return ((Long) obj).intValue();
        if (obj instanceof Double) return ((Double) obj).intValue();
        if (obj instanceof String) {
            try { return Integer.parseInt((String) obj); } catch (Exception ignore) {}
        }
        return null;
    }


    public static Map<String, Object> parseJsonObject(String s) throws JsonProcessingException {
        ObjectMapper MAPPER = new ObjectMapper();
        return MAPPER.readValue(s, new TypeReference<Map<String, Object>>() {});
    }
}
