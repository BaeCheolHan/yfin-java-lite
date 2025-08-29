package com.example.yfin;

import com.example.yfin.model.indicators.MaPoint;
import com.example.yfin.model.indicators.RsiPoint;
import com.example.yfin.service.IndicatorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@Tag(name = "Indicator API", description = "이동평균/RSI 등 기술적 지표")
@RequiredArgsConstructor
public class IndicatorController {

    private final IndicatorService indicatorService;

    @GetMapping("/indicators/ma")
    @Operation(summary = "이동평균", description = "window 구간 단순이동평균(SMA)")
    public Mono<List<MaPoint>> ma(
            @Parameter(description = "티커") @RequestParam String ticker,
            @Parameter(description = "범위 예: 3mo/6mo/1y") @RequestParam(defaultValue = "6mo") String range,
            @Parameter(description = "간격 예: 1d/1wk/1mo") @RequestParam(defaultValue = "1d") String interval,
            @Parameter(description = "자동 보정") @RequestParam(defaultValue = "true") boolean autoAdjust,
            @Parameter(description = "창 크기") @RequestParam(defaultValue = "20") int window
    ) {
        return indicatorService.ma(ticker.trim().toUpperCase(), range, interval, autoAdjust, window);
    }

    @GetMapping("/indicators/rsi")
    @Operation(summary = "RSI", description = "와일더 지수이동평균 방식")
    public Mono<List<RsiPoint>> rsi(
            @Parameter(description = "티커") @RequestParam String ticker,
            @Parameter(description = "범위 예: 6mo/1y") @RequestParam(defaultValue = "6mo") String range,
            @Parameter(description = "간격 예: 1d/1wk") @RequestParam(defaultValue = "1d") String interval,
            @Parameter(description = "자동 보정") @RequestParam(defaultValue = "true") boolean autoAdjust,
            @Parameter(description = "창 크기") @RequestParam(defaultValue = "14") int window
    ) {
        return indicatorService.rsi(ticker.trim().toUpperCase(), range, interval, autoAdjust, window);
    }
}


