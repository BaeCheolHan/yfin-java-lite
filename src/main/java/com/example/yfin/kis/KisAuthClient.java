package com.example.yfin.kis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class KisAuthClient {

    private static final Logger log = LoggerFactory.getLogger(KisAuthClient.class);

    private final WebClient kisHttp;
    private final String appKey;
    private final String appSecret;
    private final String tokenUrl;
    private final String wsApprovalUrl;
    private final AtomicReference<TokenHolder> cached = new AtomicReference<>();
    private final ReactiveStringRedisTemplate redis;

    public KisAuthClient(@Qualifier("kisHttp") WebClient kisHttp,
                         @Value("${api.kis.appKey:${kis.appKey:}}") String appKey,
                         @Value("${api.kis.app-secret:${kis.appSecret:}}") String appSecret,
                         @Value("${api.kis.access-token-generate-url:}") String tokenUrl,
                         @Value("${api.kis.approval-url:/oauth2/Approval}") String wsApprovalUrl,
                         ReactiveStringRedisTemplate redis) {
        this.kisHttp = kisHttp;
        this.appKey = appKey == null ? "" : appKey.trim();
        this.appSecret = appSecret == null ? "" : appSecret.trim();
        this.tokenUrl = tokenUrl == null ? "" : tokenUrl.trim();
        this.wsApprovalUrl = wsApprovalUrl == null ? "/oauth2/Approval" : wsApprovalUrl.trim();
        // reserved for future shared cache usage
        this.redis = redis;
    }

    public boolean isConfigured() { return !appKey.isBlank() && !appSecret.isBlank(); }

    public String appKey() { return appKey; }

    /** 토큰 가져오기(캐시). 만료 임박 시 자동 재발급 */
    public Mono<String> accessToken() {
        if (!isConfigured()) return Mono.empty();
        TokenHolder th = cached.get();
        long now = System.currentTimeMillis();
        if (th != null && now < th.expiresAtEpochMs - 30_000) {
            return Mono.just(th.accessToken);
        }
        // 1) 레거시 해시에서 조회 → 2) 없으면 발급 및 저장(이전에 남은 키는 모두 삭제 후 저장)
        return readFromLegacyHash()
                .switchIfEmpty(
                        issueToken().flatMap(tok -> deleteLegacyTokens()
                                .then(storeToLegacyHash(tok))
                                .thenReturn(tok.getAccess_token()))
                );
    }

    /** 기존 레거시 토큰 키 모두 삭제 */
    private Mono<Boolean> deleteLegacyTokens() {
        return redis.keys("RestKisToken:*")
                .collectList()
                .flatMap(keys -> {
                    if (keys == null || keys.isEmpty()) return Mono.just(Boolean.TRUE);
                    return redis.delete(keys.toArray(new String[0]))
                            .map(cnt -> Boolean.TRUE)
                            .onErrorResume(e -> Mono.just(Boolean.FALSE));
                })
                .onErrorResume(e -> Mono.just(Boolean.FALSE));
    }

    private Mono<String> readFromLegacyHash() {
        // RestKisToken:* 키 중 하나를 읽어 access_token 반환(+TTL 기반 캐시 설정)
        return redis.keys("RestKisToken:*")
                .next()
                .flatMap(key -> redis.opsForHash().get(key, "access_token")
                        .map(Object::toString)
                        .zipWith(redis.getExpire(key).defaultIfEmpty(Duration.ZERO))
                )
                .flatMap(t -> {
                    String tok = t.getT1();
                    Duration ttl = t.getT2();
                    if (tok != null && !tok.isBlank() && ttl != null && ttl.getSeconds() > 0) {
                        long expiresAt = System.currentTimeMillis() + (ttl.getSeconds() * 1000L);
                        cached.set(new TokenHolder(tok, expiresAt));
                        return Mono.just(tok);
                    }
                    return Mono.empty();
                })
                .onErrorResume(e -> Mono.empty());
    }

    private Mono<Boolean> storeToLegacyHash(KisToken tok) {
        String access = tok.getAccess_token();
        if (access == null || access.isBlank()) return Mono.just(Boolean.FALSE);
        String key = "RestKisToken:" + access;
        long ttlSec = (tok.getExpires_in() == null ? 0L : Math.max(1L, tok.getExpires_in() - 10L));
        java.time.LocalDateTime exp = java.time.LocalDateTime.now().plusSeconds(ttlSec);
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
        m.put("access_token_token_expired", exp.format(fmt));
        m.put("expires_in", String.valueOf(ttlSec));
        m.put("_class", "com.my.stock.stockmanager.redis.entity.RestKisToken");
        m.put("token_type", tok.getToken_type() == null ? "Bearer" : tok.getToken_type());
        m.put("access_token", access);

        TokenHolder nh = new TokenHolder(access, System.currentTimeMillis() + ttlSec * 1000L);
        cached.set(nh);
        return redis.opsForHash().putAll(key, m)
                .then(redis.expire(key, Duration.ofSeconds(ttlSec)))
                .onErrorResume(e -> Mono.just(Boolean.FALSE));
    }

    

    /** 토큰 발급 */
    public Mono<KisToken> issueToken() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("grant_type", "client_credentials");
        body.put("appkey", appKey);
        // REST 토큰 발급은 appsecret 필드 사용
        body.put("appsecret", appSecret);
        String uri = (tokenUrl == null || tokenUrl.isBlank()) ? "/oauth2/tokenP" : tokenUrl;
        return kisHttp.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(KisToken.class)
                .timeout(Duration.ofSeconds(8))
                .doOnError(e -> log.warn("KIS token issue failed: {}", e.toString()));
    }

    private static final class TokenHolder {
        final String accessToken;
        final long expiresAtEpochMs;
        TokenHolder(String accessToken, long expiresAtEpochMs) {
            this.accessToken = accessToken;
            this.expiresAtEpochMs = expiresAtEpochMs;
        }
    }

    /** 웹소켓 Approval Key 발급 */
    public Mono<String> issueWsApprovalKey() {
        return wsApprovalKey();
    }

    /** 웹소켓 Approval Key 조회(레디스 우선, 만료 임박 시 재발급) */
    public Mono<String> wsApprovalKey() {
        if (!isConfigured()) return Mono.empty();
        // SocketKisToken:* 해시에서 첫 키를 조회
        return redis.keys("SocketKisToken:*")
                .next()
                .flatMap(key -> redis.opsForHash().get(key, "approval_key")
                        .map(Object::toString)
                        .zipWith(redis.getExpire(key).defaultIfEmpty(Duration.ZERO))
                )
                .flatMap(t -> {
                    String existing = t.getT1();
                    Duration ttl = t.getT2();
                    if (existing != null && !existing.isBlank() && ttl != null && ttl.getSeconds() > 60) {
                        return Mono.just(existing);
                    }
                    return issueWsApprovalKeyActual();
                })
                .switchIfEmpty(issueWsApprovalKeyActual())
                .onErrorResume(e -> issueWsApprovalKeyActual());
    }

    private Mono<String> issueWsApprovalKeyActual() {
        if (!isConfigured()) return Mono.empty();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("grant_type", "client_credentials");
        body.put("appkey", appKey);
        // WS 승인키 발급은 secretkey 필드 사용
        body.put("secretkey", appSecret);
        String uri = (wsApprovalUrl == null || wsApprovalUrl.isBlank()) ? "/oauth2/Approval" : wsApprovalUrl;
        return kisHttp.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(KisSocketToken.class)
                .map(KisSocketToken::getApproval_key)
                .timeout(Duration.ofSeconds(8))
                .doOnError(e -> log.warn("KIS WS approval key issue failed: {}", e.toString()))
                .flatMap(key -> storeWsApprovalKey(key).onErrorResume(err -> Mono.just(Boolean.FALSE)).thenReturn(key));
    }

    private Mono<Boolean> storeWsApprovalKey(String key) {
        if (key == null || key.isBlank()) return Mono.just(Boolean.FALSE);
        String redisKey = "SocketKisToken:" + key;
        java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
        m.put("approval_key", key);
        // 문서 상 만료시간이 명시되지 않아 24h 기본 TTL로 저장(필요 시 조정)
        long ttlSec = 24 * 60 * 60;
        return redis.opsForHash().putAll(redisKey, m)
                .then(redis.expire(redisKey, Duration.ofSeconds(ttlSec)))
                .onErrorResume(e -> Mono.just(Boolean.FALSE));
    }
}


