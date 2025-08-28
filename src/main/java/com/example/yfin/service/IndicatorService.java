package com.example.yfin.service;

import com.example.yfin.model.HistoryResponse;
import com.example.yfin.model.HistoryRow;
import com.example.yfin.model.indicators.MaPoint;
import com.example.yfin.model.indicators.RsiPoint;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Service
public class IndicatorService {

    private final ChartService chartService;

    public IndicatorService(ChartService chartService) {
        this.chartService = chartService;
    }

    public Mono<List<MaPoint>> ma(String ticker, String range, String interval, boolean autoAdjust, int window) {
        final int w = window < 1 ? 1 : window;
        return chartService.history(ticker, range, interval, autoAdjust)
                .map(hr -> computeMa(hr, w));
    }

    public Mono<List<RsiPoint>> rsi(String ticker, String range, String interval, boolean autoAdjust, int window) {
        final int w = window < 2 ? 14 : window;
        return chartService.history(ticker, range, interval, autoAdjust)
                .map(hr -> computeRsi(hr, w));
    }

    private static List<MaPoint> computeMa(HistoryResponse hr, int window) {
        List<MaPoint> out = new ArrayList<>();
        if (hr == null || hr.getRows() == null) return out;
        List<HistoryRow> rows = hr.getRows();
        double sum = 0.0;
        int n = rows.size();
        for (int i = 0; i < n; i++) {
            Double close = rows.get(i).getClose();
            if (close != null) sum += close;
            if (i >= window) {
                Double old = rows.get(i - window).getClose();
                if (old != null) sum -= old;
            }
            if (i >= window - 1) {
                MaPoint p = new MaPoint();
                p.setTime(rows.get(i).getTime());
                p.setValue(sum / window);
                out.add(p);
            }
        }
        return out;
    }

    private static List<RsiPoint> computeRsi(HistoryResponse hr, int window) {
        List<RsiPoint> out = new ArrayList<>();
        if (hr == null || hr.getRows() == null || hr.getRows().size() < window + 1) return out;
        List<HistoryRow> rows = hr.getRows();
        double gain = 0.0, loss = 0.0;
        for (int i = 1; i <= window; i++) {
            Double prev = rows.get(i - 1).getClose();
            Double curr = rows.get(i).getClose();
            if (prev == null || curr == null) continue;
            double diff = curr - prev;
            if (diff >= 0) gain += diff; else loss += -diff;
        }
        gain /= window; loss /= window;
        for (int i = window + 1; i < rows.size(); i++) {
            Double prev = rows.get(i - 1).getClose();
            Double curr = rows.get(i).getClose();
            if (prev == null || curr == null) continue;
            double diff = curr - prev;
            double g = diff > 0 ? diff : 0.0;
            double l = diff < 0 ? -diff : 0.0;
            gain = (gain * (window - 1) + g) / window;
            loss = (loss * (window - 1) + l) / window;
            double rs = loss == 0.0 ? 100.0 : gain / loss;
            double rsi = 100.0 - (100.0 / (1.0 + rs));
            RsiPoint p = new RsiPoint();
            p.setTime(rows.get(i).getTime());
            p.setValue(rsi);
            out.add(p);
        }
        return out;
    }
}


