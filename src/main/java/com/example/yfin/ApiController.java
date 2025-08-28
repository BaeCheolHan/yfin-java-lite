package com.example.yfin;

import com.example.yfin.model.HistoryResponse;
import com.example.yfin.model.DividendsResponse;
import com.example.yfin.model.OptionsResponse;
import com.example.yfin.model.QuoteDto;
import com.example.yfin.model.SearchResponse;
import com.example.yfin.model.financials.FinancialsResponse;
import com.example.yfin.model.earnings.EarningsResponse;
import com.example.yfin.model.calendar.CalendarResponse;
import com.example.yfin.model.earnings.EarningsDatesResponse;
import com.example.yfin.model.profile.ProfileResponse;
import com.example.yfin.service.ChartService;
import com.example.yfin.service.DividendsService;
import com.example.yfin.service.FundamentalsService;
import com.example.yfin.service.OptionsService;
import com.example.yfin.service.QuoteService;
import com.example.yfin.service.SearchService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Market API", description = "시세/차트/배당/옵션/재무 API 집합")
public class ApiController {

    private final QuoteService quoteSvc;
    private final ChartService chartSvc;
    private final DividendsService divSvc;
    private final OptionsService optSvc;
    private final FundamentalsService fundSvc;
    private final SearchService searchSvc;

    public ApiController(QuoteService quoteSvc,
                         ChartService chartSvc,
                         DividendsService divSvc,
                         OptionsService optSvc,
                         FundamentalsService fundSvc,
                         SearchService searchSvc) {
        this.quoteSvc = quoteSvc;
        this.chartSvc = chartSvc;
        this.divSvc = divSvc;
        this.optSvc = optSvc;
        this.fundSvc = fundSvc;
        this.searchSvc = searchSvc;
    }

    @GetMapping("/quote")
    @Operation(summary = "단일 종목 시세 조회", description = "KR/US 티커 지원. 한국 6자리 숫자는 자동으로 거래소 접미사(.KS/.KQ)를 판별합니다. 필요 시 exchange 파라미터로 강제 지정 가능")
    public Mono<QuoteDto> quote(
            @Parameter(description = "티커. 예) 005930 또는 005930.KS, AAPL") @RequestParam String ticker,
            @Parameter(description = "거래소 접미사 수동 지정. 예) KS, KQ, NASDAQ 등") @RequestParam(required = false) String exchange) {
        String t = ticker.trim().toUpperCase();
        return exchange == null ? quoteSvc.quote(t) : quoteSvc.quoteEx(t, exchange);
    }

    @GetMapping("/quotes")
    @Operation(summary = "다중 종목 시세 조회", description = "쉼표로 구분된 티커 목록을 조회합니다. exchange로 일괄 접미사 지정 가능")
    public Mono<List<QuoteDto>> quotes(
            @Parameter(description = "티커 목록(쉼표 구분). 예) 005930,000660 또는 AAPL,MSFT") @RequestParam String tickers,
            @Parameter(description = "거래소 접미사 수동 지정(선택)") @RequestParam(required = false) String exchange) {
        List<String> list = Arrays.stream(tickers.split(","))
                .map(s -> s.trim().toUpperCase())
                .filter(s -> !s.isBlank())
                .toList();
        return exchange == null ? quoteSvc.quotes(list) : quoteSvc.quotesEx(list, exchange);
    }

    @GetMapping("/history")
    @Operation(summary = "과거 시세(HLOCV) 조회", description = "range/interval로 기간과 간격을 지정합니다. 예) range=1mo&interval=1d")
    public Mono<HistoryResponse> history(
            @Parameter(description = "티커") @RequestParam String ticker,
            @Parameter(description = "조회 범위. 예) 1d,5d,1mo,3mo,1y,5y,max") @RequestParam String range,
            @Parameter(description = "간격. 예) 1m,5m,15m,1d,1wk,1mo") @RequestParam String interval,
            @Parameter(description = "배당/분할 자동 보정 여부") @RequestParam(defaultValue = "true") boolean autoAdjust,
            @Parameter(description = "거래소 접미사 수동 지정(선택)") @RequestParam(required = false) String exchange) {
        String t = ticker.trim().toUpperCase();
        return exchange == null ? chartSvc.history(t, range, interval, autoAdjust)
                : chartSvc.historyEx(t, range, interval, autoAdjust, exchange);
    }

    @GetMapping("/dividends")
    @Operation(summary = "배당 이력 조회", description = "지정 기간의 배당 내역을 반환합니다. 기본 5y")
    public Mono<DividendsResponse> dividends(
            @Parameter(description = "티커") @RequestParam String ticker,
            @Parameter(description = "조회 범위. 기본 5y") @RequestParam(defaultValue = "5y") String range,
            @Parameter(description = "거래소 접미사 수동 지정(선택)") @RequestParam(required = false) String exchange) {
        String t = ticker.trim().toUpperCase();
        return exchange == null ? divSvc.dividends(t, range) : divSvc.dividendsEx(t, range, exchange);
    }

    @GetMapping("/options")
    @Operation(summary = "옵션 체인 조회", description = "만기일(epoch 초) 또는 미지정 시 최근 만기의 콜/풋 체인을 반환")
    public Mono<OptionsResponse> options(
            @Parameter(description = "티커") @RequestParam String ticker,
            @Parameter(description = "만기일(epoch seconds). 미지정 시 최근 만기") @RequestParam(required = false) String expiration,
            @Parameter(description = "거래소 접미사 수동 지정(선택)") @RequestParam(required = false) String exchange) {
        String t = ticker.trim().toUpperCase();
        return exchange == null ? optSvc.options(t, expiration) : optSvc.optionsEx(t, expiration, exchange);
    }

    @GetMapping("/financials")
    @Operation(summary = "재무제표 요약 조회", description = "손익계산서/대차대조표/현금흐름표(요약)를 반환")
    public Mono<FinancialsResponse> financials(
            @Parameter(description = "티커") @RequestParam String ticker,
            @Parameter(description = "거래소 접미사 수동 지정(선택)") @RequestParam(required = false) String exchange) {
        String t = ticker.trim().toUpperCase();
        return exchange == null ? fundSvc.financials(t) : fundSvc.financialsEx(t, exchange);
    }

    @GetMapping("/earnings")
    @Operation(summary = "실적/가이던스/일정 조회", description = "earnings, earningsTrend, calendarEvents 모듈")
    public Mono<EarningsResponse> earnings(
            @Parameter(description = "티커") @RequestParam String ticker,
            @Parameter(description = "거래소 접미사 수동 지정(선택)") @RequestParam(required = false) String exchange) {
        String t = ticker.trim().toUpperCase();
        return exchange == null ? fundSvc.earnings(t) : fundSvc.earningsEx(t, exchange);
    }

    @GetMapping("/calendar")
    @Operation(summary = "캘린더/이벤트 조회", description = "calendarEvents 모듈")
    public Mono<CalendarResponse> calendar(
            @Parameter(description = "티커") @RequestParam String ticker,
            @Parameter(description = "거래소 접미사 수동 지정(선택)") @RequestParam(required = false) String exchange) {
        String t = ticker.trim().toUpperCase();
        return exchange == null ? fundSvc.calendar(t) : fundSvc.calendarEx(t, exchange);
    }

    @GetMapping("/earnings/dates")
    @Operation(summary = "실적발표 일정(과거/미래)", description = "v7/finance/earnings 기반 요약")
    public Mono<EarningsDatesResponse> earningsDates(
            @Parameter(description = "티커") @RequestParam String ticker,
            @Parameter(description = "거래소 접미사 수동 지정(선택)") @RequestParam(required = false) String exchange) {
        String t = ticker.trim().toUpperCase();
        return exchange == null ? fundSvc.earningsDates(t) : fundSvc.earningsDatesEx(t, exchange);
    }

    @GetMapping("/profile")
    @Operation(summary = "기업 개요/ESG 조회", description = "summaryProfile, esgScores 모듈")
    public Mono<ProfileResponse> profile(
            @Parameter(description = "티커") @RequestParam String ticker,
            @Parameter(description = "거래소 접미사 수동 지정(선택)") @RequestParam(required = false) String exchange) {
        String t = ticker.trim().toUpperCase();
        return exchange == null ? fundSvc.profile(t) : fundSvc.profileEx(t, exchange);
    }

    @GetMapping("/search")
    @Operation(summary = "검색", description = "티커/뉴스 검색")
    public Mono<SearchResponse> search(
            @Parameter(description = "검색어") @RequestParam String q,
            @Parameter(description = "반환 개수(최대 50)") @RequestParam(defaultValue = "10") int count,
            @Parameter(description = "언어 코드, 예) ko-KR/en-US") @RequestParam(required = false) String lang,
            @Parameter(description = "지역 코드, 예) KR/US") @RequestParam(required = false) String region) {
        return searchSvc.search(q, count, lang, region);
    }

    @GetMapping("/search/google")
    @Operation(summary = "구글 뉴스 검색(RSS)", description = "Google News RSS를 사용한 뉴스 검색")
    public Mono<SearchResponse> searchGoogle(
            @Parameter(description = "검색어") @RequestParam String q,
            @Parameter(description = "반환 개수(최대 50)") @RequestParam(defaultValue = "10") int count,
            @Parameter(description = "언어 코드, 예) ko") @RequestParam(required = false) String lang) {
        return searchSvc.searchGoogle(q, count, lang);
    }

    // 대체 경로(일부 환경에서 /search/google 정적 리소스 충돌 시 사용)
    @GetMapping("/news/google")
    @Operation(summary = "구글 뉴스 검색(RSS, 대체 경로)", description = "Google News RSS 대체 엔드포인트")
    public Mono<SearchResponse> newsGoogle(
            @Parameter(description = "검색어") @RequestParam String q,
            @Parameter(description = "반환 개수(최대 50)") @RequestParam(defaultValue = "10") int count,
            @Parameter(description = "언어 코드, 예) ko") @RequestParam(required = false) String lang) {
        return searchSvc.searchGoogle(q, count, lang);
    }
}