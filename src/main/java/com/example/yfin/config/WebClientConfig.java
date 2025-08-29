package com.example.yfin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import com.example.yfin.http.CookieStore;

@Configuration
public class WebClientConfig {

    private static WebClient mk(String baseUrl) {
        HttpClient http = HttpClient.create()
                .followRedirect(true)
                .responseTimeout(java.time.Duration.ofSeconds(12))
                .httpResponseDecoder(h -> h
                        .maxHeaderSize(64 * 1024)
                        .maxInitialLineLength(8 * 1024));

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(http))
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(c -> c.defaultCodecs().maxInMemorySize(8 * 1024 * 1024))
                        .build())
                .defaultHeader("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                                + "(KHTML, like Gecko) Chrome Safari")
                .build();
    }

    @Bean public WebClient yahooApiClient1() { return mk("https://query1.finance.yahoo.com"); }
    @Bean public WebClient yahooApiClient2() { return mk("https://query2.finance.yahoo.com"); }

    // Beans used by MarketService (qualified injection)
    @Bean("yahooClient")
    public WebClient yahooClient() {
        return mk("https://query2.finance.yahoo.com");
    }

    @Bean("browserClient")
    public WebClient browserClient() {
        return mk("https://finance.yahoo.com");
    }

    @Bean("alphaVantageHttp")
    public WebClient alphaVantageHttp() {
        return mk("https://www.alphavantage.co");
    }

    @Bean("finnhubHttp")
    public WebClient finnhubHttp() {
        return mk("https://finnhub.io");
    }

    @Bean("kisHttp")
    public WebClient kisHttp(@org.springframework.beans.factory.annotation.Value("${api.kis.base-url:https://openapi.koreainvestment.com:9443}") String baseUrl) {
        return mk(baseUrl);
    }

    @Bean
    public CookieStore cookieStore() {
        return new CookieStore();
    }

    @Bean("googleNewsClient")
    public WebClient googleNewsClient() {
        return mk("https://news.google.com");
    }
}