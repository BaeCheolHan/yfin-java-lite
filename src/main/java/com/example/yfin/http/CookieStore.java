package com.example.yfin.http;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class CookieStore {
    private final Map<String, String> jar = new ConcurrentHashMap<>();

    public void put(String name, String value) {
        if (name != null && !name.isBlank()) {
            jar.put(name.trim(), value == null ? "" : value);
        }
    }

    /** 요청에 보낼 Cookie 헤더 값 */
    public String asCookieHeader() {
        if (jar.isEmpty()) return "";
        return jar.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("; "));
    }

    /** 디버깅용 */
    public Map<String, String> snapshot() {
        return Map.copyOf(jar);
    }

    /**
     * Set-Cookie 한 줄에서 name=value 추출 (path, expires 등 제거)
     * e.g. "A1=abc; Expires=...; Path=/; Secure" -> "A1=abc"
     */
    public static String extractNameValue(String setCookieLine) {
        if (setCookieLine == null) return null;
        int semi = setCookieLine.indexOf(';');
        String first = semi > 0 ? setCookieLine.substring(0, semi) : setCookieLine;
        int eq = first.indexOf('=');
        return (eq > 0) ? first : null;
    }
}