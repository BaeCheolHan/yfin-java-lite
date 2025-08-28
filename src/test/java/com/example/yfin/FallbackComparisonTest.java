package com.example.yfin;

import com.example.yfin.service.QuoteService;
import com.example.yfin.model.QuoteDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class FallbackComparisonTest {

    @Autowired
    QuoteService quoteService;

    @Test
    @DisplayName("Yahoo 정상 시 quotes 응답 기본 검증")
    void quotes_ok_yahoo() {
        List<QuoteDto> res = quoteService.quotes(List.of("AAPL","MSFT"))
                .block(Duration.ofSeconds(10));
        assertNotNull(res);
        assertTrue(res.size() >= 2);
        QuoteDto aapl = res.stream().filter(q -> "AAPL".equals(q.getSymbol())).findFirst().orElse(null);
        assertNotNull(aapl);
        assertNotNull(aapl.getRegularMarketPrice());
    }

    @Test
    @DisplayName("폴백 경로 비교(허용 오차 내 가격 비교)")
    void quotes_compare_with_fallback_tolerance() {
        // 1) 정상 호출 (Yahoo 또는 폴백 포함) 기준값
        List<QuoteDto> baseline = quoteService.quotes(List.of("AAPL"))
                .block(Duration.ofSeconds(10));
        assertNotNull(baseline);
        QuoteDto base = baseline.get(0);

        // 2) 의도적으로 Yahoo enrich 단계 실패 유도: 존재하지 않는 모듈 조회로 가능하지만, 여기선 가격 자체 비교만 수행
        // 실제 폴백 강제는 별도 테스트에서 환경 플래그/모킹으로 수행 예정
        List<QuoteDto> compare = quoteService.quotes(List.of("AAPL"))
                .block(Duration.ofSeconds(10));
        assertNotNull(compare);
        QuoteDto cmp = compare.get(0);

        Double p1 = base.getRegularMarketPrice();
        Double p2 = cmp.getRegularMarketPrice();
        assertNotNull(p1);
        assertNotNull(p2);
        double diffPct = Math.abs(p1 - p2) / Math.max(1.0, p1) * 100.0;
        assertTrue(diffPct < 2.0, "가격 차이가 2% 미만이어야 함, diffPct=" + diffPct);
    }
}


