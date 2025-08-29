package com.example.yfin.service;

import com.example.yfin.model.QuoteDto;
import com.example.yfin.repo.ListingMetaRepository;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScreenerService {

    private final ListingMetaRepository listingRepo;
    private final QuoteService quoteService;

    public Mono<List<QuoteDto>> filterBy(String market,
                                         Double minDividendYield,
                                         Double minVolatilityPct,
                                         Long minVolume) {
        return listingRepo.findAll()
                .filter(doc -> market == null || market.equalsIgnoreCase(doc.getMarket()))
                .map(doc -> doc.getId())
                .collectList()
                .flatMap(ids -> quoteService.quotes(ids))
                .map(list -> {
                    List<QuoteDto> out = new ArrayList<>();
                    for (QuoteDto q : list) {
                        if (q == null) continue;
                        if (minDividendYield != null) {
                            Double dy = q.getForwardDividendYield();
                            if (dy == null) dy = q.getTrailingAnnualDividendYield();
                            if (dy == null || dy < minDividendYield) continue;
                        }
                        if (minVolume != null) {
                            if (q.getRegularMarketVolume() == null || q.getRegularMarketVolume() < minVolume) continue;
                        }
                        // 변동성은 현 시점에 직접 계산 정보가 없으므로 dayHigh/Low 기반 근사(간단): (high-low)/price
                        if (minVolatilityPct != null && minVolatilityPct > 0) {
                            Double p = q.getRegularMarketPrice();
                            Double hi = q.getDayHigh();
                            Double lo = q.getDayLow();
                            if (p == null || p <= 0 || hi == null || lo == null) continue;
                            double approx = (hi - lo) / p * 100.0;
                            if (approx < minVolatilityPct) continue;
                        }
                        out.add(q);
                    }
                    return out;
                });
    }

    public Mono<List<Map.Entry<String, List<QuoteDto>>>> rankBySectorTopN(String market, int topN, String sortBy) {
        return listingRepo.findAll()
                .filter(doc -> market == null || market.equalsIgnoreCase(doc.getMarket()))
                .collectList()
                .flatMap(docs -> {
                    List<String> ids = docs.stream().map(d -> d.getId()).collect(Collectors.toList());
                    return quoteService.quotes(ids).map(quotes -> {
                        Map<String, List<QuoteDto>> grouped = docs.stream()
                                .collect(Collectors.groupingBy(
                                        d -> d.getSector() == null ? "UNKNOWN" : d.getSector(),
                                        Collectors.mapping(d -> findQuote(quotes, d.getId()), Collectors.toList())
                                ));
                        Comparator<QuoteDto> cmp = buildComparator(sortBy);
                        List<Map.Entry<String, List<QuoteDto>>> ranked = new ArrayList<>(grouped.entrySet());
                        for (Map.Entry<String, List<QuoteDto>> e : ranked) {
                            List<QuoteDto> vs = e.getValue().stream().filter(q -> q != null).sorted(cmp.reversed()).limit(topN).collect(Collectors.toList());
                            e.setValue(vs);
                        }
                        ranked.sort((a,b) -> Double.compare(score(a.getValue()), score(b.getValue())));
                        return ranked;
                    });
                });
    }

    private static Comparator<QuoteDto> buildComparator(String sortBy) {
        if ("volume".equalsIgnoreCase(sortBy)) {
            return Comparator.comparing(q -> q.getRegularMarketVolume() == null ? 0L : q.getRegularMarketVolume());
        }
        // 기본: 수익률 기준
        return Comparator.comparing(q -> q.getRegularMarketChangePercent() == null ? 0.0 : q.getRegularMarketChangePercent());
    }

    private static double score(List<QuoteDto> list) {
        if (list == null || list.isEmpty()) return 0.0;
        double sum = 0.0;
        for (QuoteDto q : list) sum += (q.getRegularMarketChangePercent() == null ? 0.0 : q.getRegularMarketChangePercent());
        return sum;
    }

    private static QuoteDto findQuote(List<QuoteDto> quotes, String symbol) {
        for (QuoteDto q : quotes) {
            if (q != null && symbol.equalsIgnoreCase(q.getSymbol())) return q;
        }
        return null;
    }
}


