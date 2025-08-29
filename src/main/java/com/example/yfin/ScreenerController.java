package com.example.yfin;

import com.example.yfin.model.QuoteDto;
import com.example.yfin.service.ScreenerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@Tag(name = "Screener API", description = "필터/랭킹 스크리너")
@RequiredArgsConstructor
public class ScreenerController {

    private final ScreenerService screenerService;

    @GetMapping("/screener/filter")
    @Operation(summary = "기본 필터 스크리너", description = "시장/배당수익률/근사 변동성/최소 거래량 필터")
    public Mono<List<QuoteDto>> filter(
            @Parameter(description = "시장 코드 예: KS/KQ/NASDAQ") @RequestParam(required = false) String market,
            @Parameter(description = "최소 배당수익률(소수)") @RequestParam(required = false) Double minDividendYield,
            @Parameter(description = "최소 변동성(%, 근사)") @RequestParam(required = false) Double minVolatilityPct,
            @Parameter(description = "최소 거래량") @RequestParam(required = false) Long minVolume
    ) {
        return screenerService.filterBy(market, minDividendYield, minVolatilityPct, minVolume);
    }

    @GetMapping("/screener/sector/ranking")
    @Operation(summary = "섹터별 랭킹", description = "섹터별 상위 N 종목(변동률 또는 거래량 기준)")
    public Mono<List<Map.Entry<String, List<QuoteDto>>>> sectorRanking(
            @Parameter(description = "시장 코드 예: KS/KQ/NASDAQ") @RequestParam(required = false) String market,
            @Parameter(description = "섹터 내 상위 개수") @RequestParam(defaultValue = "5") int topN,
            @Parameter(description = "정렬 기준: changePercent/volume") @RequestParam(defaultValue = "changePercent") String sortBy
    ) {
        return screenerService.rankBySectorTopN(market, topN, sortBy);
    }
}


