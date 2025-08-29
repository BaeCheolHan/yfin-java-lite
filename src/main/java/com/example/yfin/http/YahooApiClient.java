package com.example.yfin.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.Map;

@Component
public class YahooApiClient {

    private static final Logger log = LoggerFactory.getLogger(YahooApiClient.class);

    private final WebClient yahooClient;   // default
    private final WebClient yahooClient1;  // query1
    private final WebClient yahooClient2;  // query2
    private final WebClient browserClient; // finance.yahoo.com
    private final CookieStore cookieStore;

    private volatile String crumb;
    private final AtomicInteger uaIndex = new AtomicInteger(0);
    private static final String[] USER_AGENTS = new String[] {
            // 다양한 브라우저 UA 회전으로 Anti-bot 완화
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome Safari",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.3 Safari/605.1.15",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome Safari",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:120.0) Gecko/20100101 Firefox/120.0"
    };

    public YahooApiClient(@Qualifier("yahooClient") WebClient yahooClient,
                          @Qualifier("yahooApiClient1") WebClient yahooClient1,
                          @Qualifier("yahooApiClient2") WebClient yahooClient2,
                          @Qualifier("browserClient") WebClient browserClient,
                          CookieStore cookieStore) {
        this.yahooClient = yahooClient;
        this.yahooClient1 = yahooClient1;
        this.yahooClient2 = yahooClient2;
        this.browserClient = browserClient;
        this.cookieStore = cookieStore;
    }

    public Mono<Map<String, Object>> getJson(String path, String refererPath) {
        long started = System.nanoTime();
        return warmup(refererPath)
                .then(ensureCrumb())
                .defaultIfEmpty("")
                .flatMap(c1 -> doRequestJson(yahooClient2, attachCrumb(path, c1), refererPath))
                .onErrorResume(this::isBlocked, e ->
                        Mono.delay(Duration.ofMillis(250))
                                .then(invalidateCrumb())
                                .then(ensureCrumb().defaultIfEmpty(""))
                                .flatMap(c2 -> doRequestJson(yahooClient1, attachCrumb(path, c2), refererPath)))
                .onErrorResume(this::isBlocked, e ->
                        Mono.delay(Duration.ofMillis(350))
                                .then(invalidateCrumb())
                                .then(ensureCrumb().defaultIfEmpty(""))
                                .flatMap(c3 -> doRequestJson(yahooClient, attachCrumb(path, c3), refererPath)))
                .onErrorResume(this::shouldRetryAntiBot, e -> {
                    long jitter = ThreadLocalRandom.current().nextLong(500, 1200);
                    return Mono.delay(Duration.ofMillis(jitter))
                            .then(invalidateCrumb())
                            .then(ensureCrumb().defaultIfEmpty(""))
                            .flatMap(c4 -> doRequestJson(yahooClient2, attachCrumb(path, c4), refererPath));
                })
                .doOnSuccess(r -> log.debug("Yahoo GET {} took {} ms", path, (System.nanoTime()-started)/1_000_000))
                .doOnError(err -> log.warn("Yahoo GET {} failed after {} ms: {}", path, (System.nanoTime()-started)/1_000_000, err.toString()));
    }

    public Mono<String> getText(String path, String refererPath) {
        return warmup(refererPath)
                .then(doRequestText(yahooClient2, path, refererPath)
                        .onErrorResume(this::isBlocked, e -> doRequestText(yahooClient1, path, refererPath))
                        .onErrorResume(this::isBlocked, e -> doRequestText(yahooClient, path, refererPath))
                );
    }

    private Mono<Void> warmup(String refererPath) {
        String uri = (refererPath == null || refererPath.isBlank()) ? "/" : refererPath;
        return browserClient.get().uri(uri)
                .accept(MediaType.TEXT_HTML)
                .exchangeToMono(resp -> {
                    List<String> setCookies = resp.headers().header("Set-Cookie");
                    for (String line : setCookies) {
                        String nv = CookieStore.extractNameValue(line);
                        if (nv != null) {
                            int eq = nv.indexOf('=');
                            if (eq > 0) {
                                String name = nv.substring(0, eq);
                                String value = nv.substring(eq + 1);
                                cookieStore.put(name, value);
                            }
                        }
                    }
                    return resp.releaseBody();
                })
                .onErrorResume(e -> Mono.empty());
    }

    private Mono<Map<String, Object>> doRequestJson(WebClient client, String path, String refererPath) {
        return client.get().uri(path)
                .accept(MediaType.APPLICATION_JSON)
                .headers(h -> {
                    h.set("Accept", "application/json, text/javascript, */*; q=0.01");
                    h.set("Accept-Language", "en-US,en;q=0.9");
                    // Accept-Encoding은 서버가 압축을 선택하도록 비설정(기본)으로 둔다
                    h.set("User-Agent", nextUserAgent());
                    h.set("Connection", "keep-alive");
                    h.set("Cache-Control", "no-cache");
                    h.set("Pragma", "no-cache");
                    h.set("DNT", "1");
                    h.set("Upgrade-Insecure-Requests", "1");
                    h.set("sec-fetch-site", "same-origin");
                    h.set("sec-fetch-mode", "cors");
                    h.set("sec-fetch-dest", "empty");
                    h.set("Origin", "https://finance.yahoo.com");
                    if (refererPath != null && !refererPath.isBlank()) {
                        h.set("Referer", "https://finance.yahoo.com" + refererPath);
                    }
                    String cookie = cookieStore.asCookieHeader();
                    if (!cookie.isBlank()) h.set("Cookie", cookie);
                })
                .exchangeToMono(this::handleJson);
    }

    private Mono<String> doRequestText(WebClient client, String path, String refererPath) {
        return client.get().uri(path)
                .accept(MediaType.TEXT_PLAIN)
                .headers(h -> {
                    h.set("Accept-Language", "en-US,en;q=0.9");
                    h.set("Accept-Encoding", "identity");
                    h.set("Origin", "https://finance.yahoo.com");
                    if (refererPath != null && !refererPath.isBlank()) {
                        h.set("Referer", "https://finance.yahoo.com" + refererPath);
                    }
                    String cookie = cookieStore.asCookieHeader();
                    if (!cookie.isBlank()) h.set("Cookie", cookie);
                })
                .exchangeToMono(resp -> {
                    int code = resp.statusCode().value();
                    if (code >= 400) {
                        return resp.releaseBody().then(Mono.error(new RuntimeException("Yahoo text error (" + code + ")")));
                    }
                    return resp.bodyToMono(String.class);
                });
    }

    private Mono<Map<String, Object>> handleJson(ClientResponse resp) {
        int code = resp.statusCode().value();
        if (code == 401 || code == 403) {
            return resp.releaseBody().then(Mono.error(new RuntimeException("Yahoo blocked (" + code + ")")));
        }
        String ct = resp.headers().contentType().map(MediaType::toString).orElse("<none>");
        String ce = String.join(",", resp.headers().header("Content-Encoding"));
        if (!ct.contains("json")) {
            return resp.bodyToMono(String.class)
                    .flatMap(body -> {
                        String preview = body.length() > 256 ? body.substring(0, 256) + "..." : body;
                        log.warn("Yahoo non-JSON response: contentType={}, contentEncoding={}, preview={}",
                                ct, ce, preview.replace('\n',' ').replace('\r',' '));
                        return Mono.error(new RuntimeException("Yahoo returned non-JSON (" + ct + ")"));
                    });
        }
        return resp.bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    public Mono<String> ensureCrumb() {
        if (crumb != null && !crumb.isBlank()) return Mono.just(crumb);
        return tryGetCrumb(yahooClient2)
                .onErrorResume(e -> tryGetCrumb(yahooClient1))
                .doOnNext(c -> this.crumb = c)
                .onErrorResume(e -> Mono.empty());
    }

    public Mono<Void> invalidateCrumb() {
        this.crumb = null;
        return Mono.empty();
    }

    private String attachCrumb(String path, String c) {
        if (c == null || c.isBlank()) return path;
        return path + (path.contains("?") ? "&" : "?") + "crumb=" + c;
    }

    private Mono<String> tryGetCrumb(WebClient client) {
        return client.get().uri("/v1/test/getcrumb")
                .accept(MediaType.TEXT_PLAIN)
                .headers(h -> {
                    h.set("Accept-Language", "en-US,en;q=0.9");
                    h.set("Origin", "https://finance.yahoo.com");
                    h.set("Referer", "https://finance.yahoo.com/");
                    String cookie = cookieStore.asCookieHeader();
                    if (!cookie.isBlank()) h.set("Cookie", cookie);
                })
                .exchangeToMono(resp -> {
                    int code = resp.statusCode().value();
                    if (code >= 400) {
                        return resp.releaseBody().then(Mono.error(new RuntimeException("crumb fetch failed (" + code + ")")));
                    }
                    return resp.bodyToMono(String.class).map(String::trim).filter(s -> !s.isBlank());
                });
    }

    private boolean isBlocked(Throwable e) {
        if (e == null) return false;
        if (e instanceof WebClientResponseException w) {
            int s = w.getStatusCode().value();
            return s == 401 || s == 403;
        }
        String msg = e.getMessage();
        return msg != null && (msg.contains("Yahoo blocked (401)") || msg.contains("Yahoo blocked (403)"));
    }

    private boolean shouldRetryAntiBot(Throwable e) {
        if (isBlocked(e)) return true;
        String msg = (e == null) ? null : e.getMessage();
        return msg != null && (msg.contains("non-JSON") || msg.contains("crumb fetch failed") || msg.contains("connection reset"));
    }

    private String nextUserAgent() {
        int idx = Math.abs(uaIndex.getAndIncrement());
        return USER_AGENTS[idx % USER_AGENTS.length];
    }
}


