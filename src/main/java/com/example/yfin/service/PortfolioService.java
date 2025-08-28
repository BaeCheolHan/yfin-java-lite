package com.example.yfin.service;

import com.example.yfin.model.QuoteDto;
import com.example.yfin.model.portfolio.PortfolioSummary;
import com.example.yfin.model.portfolio.PositionDto;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Service
public class PortfolioService {

    private final QuoteService quoteService;

    public PortfolioService(QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    public Mono<PortfolioSummary> summarize(List<PositionDto> positions) {
        if (positions == null || positions.isEmpty()) return Mono.just(new PortfolioSummary());
        List<String> symbols = new ArrayList<>(positions.size());
        for (PositionDto p : positions) symbols.add(p.getSymbol().trim().toUpperCase());
        return quoteService.quotes(symbols).map(quotes -> compute(positions, quotes));
    }

    private static PortfolioSummary compute(List<PositionDto> positions, List<QuoteDto> quotes) {
        PortfolioSummary sum = new PortfolioSummary();
        double marketValue = 0.0, cost = 0.0, dividendAnnual = 0.0;
        List<PortfolioSummary.PositionBreakdown> items = new ArrayList<>();
        for (PositionDto p : positions) {
            QuoteDto q = find(quotes, p.getSymbol());
            PortfolioSummary.PositionBreakdown it = new PortfolioSummary.PositionBreakdown();
            it.setSymbol(p.getSymbol());
            it.setQuantity(n(p.getQuantity()));
            Double price = q == null ? null : q.getRegularMarketPrice();
            it.setPrice(price);
            double mv = (price == null ? 0.0 : price) * n(p.getQuantity());
            double c = n(p.getAverageCost()) * n(p.getQuantity());
            it.setMarketValue(mv);
            it.setCost(c);
            it.setPnl(mv - c);
            it.setPnlRate(c == 0.0 ? null : (mv - c) / c);
            Double dy = q == null ? null : (q.getForwardDividendYield() != null ? q.getForwardDividendYield() : q.getTrailingAnnualDividendYield());
            Double dr = q == null ? null : (q.getForwardDividendRate() != null ? q.getForwardDividendRate() : q.getTrailingAnnualDividendRate());
            Double annual = null;
            if (dr != null) annual = dr * n(p.getQuantity());
            else if (dy != null && price != null) annual = dy * price * n(p.getQuantity());
            it.setDividendAnnual(annual);
            it.setDividendYield(price == null || annual == null ? null : (annual / mv));
            items.add(it);
            marketValue += mv;
            cost += c;
            dividendAnnual += (annual == null ? 0.0 : annual);
        }
        sum.setItems(items);
        sum.setMarketValue(marketValue);
        sum.setCost(cost);
        sum.setPnl(marketValue - cost);
        sum.setPnlRate(cost == 0.0 ? null : (marketValue - cost) / cost);
        sum.setDividendAnnual(dividendAnnual);
        sum.setDividendYield(marketValue == 0.0 ? null : dividendAnnual / marketValue);
        return sum;
    }

    private static QuoteDto find(List<QuoteDto> list, String symbol) {
        if (list == null) return null;
        for (QuoteDto q : list) if (q.getSymbol().equalsIgnoreCase(symbol)) return q;
        return null;
    }

    private static double n(Double v) { return v == null ? 0.0 : v; }
}



