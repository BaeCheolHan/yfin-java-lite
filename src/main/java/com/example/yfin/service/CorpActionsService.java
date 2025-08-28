package com.example.yfin.service;

import com.example.yfin.model.DivRow;
import com.example.yfin.model.calendar.CalendarResponse;
import com.example.yfin.model.corp.CorpActionsResponse;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class CorpActionsService {

    private final FundamentalsService fundamentalsService;
    private final DividendsService dividendsService;

    public CorpActionsService(FundamentalsService fundamentalsService, DividendsService dividendsService) {
        this.fundamentalsService = fundamentalsService;
        this.dividendsService = dividendsService;
    }

    @Cacheable(cacheNames = "corpActions", key = "#ticker")
    public Mono<CorpActionsResponse> corp(String ticker) {
        Mono<CalendarResponse> cal = fundamentalsService.calendar(ticker);
        Mono<com.example.yfin.model.DividendsResponse> div = dividendsService.dividends(ticker, "3y");
        Mono<java.util.List<String>> splits = dividendsService.splits(ticker, "10y");
        return Mono.zip(cal.defaultIfEmpty(new CalendarResponse()),
                        div.defaultIfEmpty(new com.example.yfin.model.DividendsResponse()),
                        splits.defaultIfEmpty(java.util.List.of()))
                .map(t -> {
                    CalendarResponse c = t.getT1();
                    com.example.yfin.model.DividendsResponse d = t.getT2();
                    java.util.List<String> sp = t.getT3();
                    CorpActionsResponse res = new CorpActionsResponse();
                    res.setTicker(ticker);
                    res.setExDividendDate(c.getExDividendDate());
                    res.setDividendDate(c.getDividendDate());
                    List<DivRow> recent = d.getRows() == null ? List.of() : d.getRows();
                    res.setRecentDividends(recent.size() > 10 ? recent.subList(recent.size() - 10, recent.size()) : recent);
                    res.setRecentSplits(sp.size() > 10 ? sp.subList(sp.size() - 10, sp.size()) : sp);
                    return res;
                });
    }
}


