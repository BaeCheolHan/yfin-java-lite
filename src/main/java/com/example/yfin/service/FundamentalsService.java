package com.example.yfin.service;

import com.example.yfin.http.YahooApiClient;
import com.example.yfin.model.earnings.EarningsResponse;
import com.example.yfin.model.calendar.CalendarResponse;
import com.example.yfin.model.earnings.EarningsDatesResponse;
import com.example.yfin.model.financials.FinancialsResponse;
import com.example.yfin.model.profile.ProfileResponse;
import com.example.yfin.model.financials.FinancialsDto;
import com.example.yfin.model.earnings.EarningsTrendDto;
import com.example.yfin.model.profile.SummaryProfileDto;
import com.example.yfin.model.profile.EsgScoresDto;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Service
public class FundamentalsService {
    private final YahooApiClient yahoo;
    private final TickerResolver resolver;
    private final com.example.yfin.service.cache.RedisCacheService l2;
    private final com.example.yfin.repo.ListingMetaRepository listingRepo;
    private final com.example.yfin.http.DartClient dart;

    public FundamentalsService(YahooApiClient yahoo, TickerResolver resolver, com.example.yfin.service.cache.RedisCacheService l2,
                               com.example.yfin.repo.ListingMetaRepository listingRepo,
                               com.example.yfin.http.DartClient dart) {
        this.yahoo = yahoo;
        this.resolver = resolver;
        this.l2 = l2;
        this.listingRepo = listingRepo;
        this.dart = dart;
    }

    private EarningsDatesResponse mapEarningsDates(String ticker, Map<String, Object> body) {
        EarningsDatesResponse response = new EarningsDatesResponse();
        response.setTicker(ticker);
        Map<String, Object> quoteSummary = asMap(body.get("quoteSummary"));
        if (quoteSummary == null) return response;
        java.util.List<Map<String, Object>> result = asListOfMap(quoteSummary.get("result"));
        if (result == null || result.isEmpty()) return response;
        Map<String, Object> r0 = result.get(0);
        Map<String, Object> earnings = asMap(r0.get("earnings"));
        if (earnings == null) return response;

        Map<String, Object> earningsChart = asMap(earnings.get("earningsChart"));
        if (earningsChart == null) return response;

        com.example.yfin.model.earnings.EarningsSummary summary = new com.example.yfin.model.earnings.EarningsSummary();

        java.util.List<?> quarterly = asList(earningsChart.get("quarterly"));
        if (quarterly != null) {
            java.util.List<com.example.yfin.model.earnings.QuarterlyEarnings> list = new java.util.ArrayList<>();
            for (Object item : quarterly) {
                Map<String, Object> m = asMap(item);
                if (m == null) continue;
                com.example.yfin.model.earnings.QuarterlyEarnings qe = new com.example.yfin.model.earnings.QuarterlyEarnings();
                qe.setDate(asString(m.get("date")));
                qe.setActual(extractRawDouble(m.get("actual")));
                qe.setEstimate(extractRawDouble(m.get("estimate")));
                list.add(qe);
            }
            summary.setQuarterly(list);
        }

        Double cqe = extractRawDouble(earningsChart.get("currentQuarterEstimate"));
        if (cqe != null) summary.setCurrentQuarterEstimate(cqe);
        String cqed = asString(earningsChart.get("currentQuarterEstimateDate"));
        if (cqed != null) summary.setCurrentQuarterEstimateDate(cqed);
        Integer cqey = extractInteger(earningsChart.get("currentQuarterEstimateYear"));
        if (cqey != null) summary.setCurrentQuarterEstimateYear(cqey);
        com.example.yfin.model.common.FormattedDate next = toFormattedDate(earningsChart.get("earningsDate"));
        if (next != null) summary.setNextEarningsDate(next);

        response.setSummary(summary);
        return response;
    }
    @Cacheable(cacheNames = "financials", key = "#ticker")
    public Mono<FinancialsResponse> financials(String ticker) {
        String modules = "incomeStatementHistory,balanceSheetHistory,cashflowStatementHistory";
        return resolver.normalize(ticker).flatMap(nt -> {
            String path = "/v10/finance/quoteSummary/" + nt + "?modules=" + modules + "&lang=en-US&region=US&corsDomain=finance.yahoo.com";
            String key = "financials:" + nt;
            return l2.get(key, new com.fasterxml.jackson.core.type.TypeReference<com.example.yfin.model.financials.FinancialsResponse>() {})
                    .switchIfEmpty(
                            yahoo.getJson(path, "/quote/" + nt)
                                    .map(m -> mapFinancials(nt, m))
                                    .flatMap(res -> l2.set(key, res, java.time.Duration.ofSeconds(30)).thenReturn(res))
                    );
        });
    }

    public Mono<FinancialsResponse> financialsEx(String ticker, String exchange) {
        String modules = "incomeStatementHistory,balanceSheetHistory,cashflowStatementHistory";
        return resolver.normalize(ticker, exchange).flatMap(nt -> {
            String path = "/v10/finance/quoteSummary/" + nt + "?modules=" + modules + "&lang=en-US&region=US&corsDomain=finance.yahoo.com";
            String key = "financials:" + nt;
            return l2.get(key, new com.fasterxml.jackson.core.type.TypeReference<com.example.yfin.model.financials.FinancialsResponse>() {})
                    .switchIfEmpty(
                            yahoo.getJson(path, "/quote/" + nt)
                                    .map(m -> mapFinancials(nt, m))
                                    .flatMap(res -> l2.set(key, res, java.time.Duration.ofSeconds(30)).thenReturn(res))
                    );
        });
    }

    @Cacheable(cacheNames = "earnings", key = "#ticker")
    public Mono<EarningsResponse> earnings(String ticker) {
        String modules = "earnings,earningsTrend,calendarEvents";
        return resolver.normalize(ticker).flatMap(nt -> {
            String path = "/v10/finance/quoteSummary/" + nt + "?modules=" + modules + "&lang=en-US&region=US&corsDomain=finance.yahoo.com";
            String key = "earnings:" + nt;
            return l2.get(key, new com.fasterxml.jackson.core.type.TypeReference<com.example.yfin.model.earnings.EarningsResponse>() {})
                    .switchIfEmpty(
                            yahoo.getJson(path, "/quote/" + nt)
                                    .map(m -> mapEarnings(nt, m))
                                    .flatMap(res -> l2.set(key, res, java.time.Duration.ofSeconds(30)).thenReturn(res))
                    );
        });
    }

    @Cacheable(cacheNames = "earnings", key = "#ticker + ':' + #exchange")
    public Mono<EarningsResponse> earningsEx(String ticker, String exchange) {
        String modules = "earnings,earningsTrend,calendarEvents";
        return resolver.normalize(ticker, exchange).flatMap(nt -> {
            String path = "/v10/finance/quoteSummary/" + nt + "?modules=" + modules + "&lang=en-US&region=US&corsDomain=finance.yahoo.com";
            String key = "earnings:" + nt;
            return l2.get(key, new com.fasterxml.jackson.core.type.TypeReference<com.example.yfin.model.earnings.EarningsResponse>() {})
                    .switchIfEmpty(
                            yahoo.getJson(path, "/quote/" + nt)
                                    .map(m -> mapEarnings(nt, m))
                                    .flatMap(res -> l2.set(key, res, java.time.Duration.ofSeconds(30)).thenReturn(res))
                    );
        });
    }

    @Cacheable(cacheNames = "profile", key = "#ticker")
    public Mono<ProfileResponse> profile(String ticker) {
        String modules = "summaryProfile,esgScores";
        return resolver.normalize(ticker).flatMap(nt -> {
            String path = "/v10/finance/quoteSummary/" + nt + "?modules=" + modules + "&lang=en-US&region=US&corsDomain=finance.yahoo.com";
            String key = "profile:" + nt;
            return l2.get(key, new com.fasterxml.jackson.core.type.TypeReference<com.example.yfin.model.profile.ProfileResponse>() {})
                    .switchIfEmpty(
                            yahoo.getJson(path, "/quote/" + nt)
                                    .map(m -> mapProfile(nt, m))
                                    .flatMap(res -> enrichProfileWithMetaAndDart(nt, res))
                                    .flatMap(res -> l2.set(key, res, java.time.Duration.ofSeconds(60)).thenReturn(res))
                    );
        });
    }

    @Cacheable(cacheNames = "calendar", key = "#ticker")
    public Mono<CalendarResponse> calendar(String ticker) {
        String modules = "calendarEvents";
        return resolver.normalize(ticker).flatMap(nt -> {
            String path = "/v10/finance/quoteSummary/" + nt + "?modules=" + modules + "&lang=en-US&region=US&corsDomain=finance.yahoo.com";
            String key = "calendar:" + nt;
            return l2.get(key, new com.fasterxml.jackson.core.type.TypeReference<com.example.yfin.model.calendar.CalendarResponse>() {})
                    .switchIfEmpty(
                            yahoo.getJson(path, "/quote/" + nt)
                                    .map(m -> mapCalendar(nt, m))
                                    .flatMap(res -> l2.set(key, res, java.time.Duration.ofSeconds(60)).thenReturn(res))
                    );
        });
    }

    @Cacheable(cacheNames = "calendar", key = "#ticker + ':' + #exchange")
    public Mono<CalendarResponse> calendarEx(String ticker, String exchange) {
        String modules = "calendarEvents";
        return resolver.normalize(ticker, exchange).flatMap(nt -> {
            String path = "/v10/finance/quoteSummary/" + nt + "?modules=" + modules + "&lang=en-US&region=US&corsDomain=finance.yahoo.com";
            String key = "calendar:" + nt;
            return l2.get(key, new com.fasterxml.jackson.core.type.TypeReference<com.example.yfin.model.calendar.CalendarResponse>() {})
                    .switchIfEmpty(
                            yahoo.getJson(path, "/quote/" + nt)
                                    .map(m -> mapCalendar(nt, m))
                                    .flatMap(res -> l2.set(key, res, java.time.Duration.ofSeconds(60)).thenReturn(res))
                    );
        });
    }

    @Cacheable(cacheNames = "earningsDates", key = "#ticker")
    public Mono<EarningsDatesResponse> earningsDates(String ticker) {
        String modules = "earnings,calendarEvents";
        return resolver.normalize(ticker).flatMap(nt -> {
            String path = "/v10/finance/quoteSummary/" + nt + "?modules=" + modules + "&lang=en-US&region=US&corsDomain=finance.yahoo.com";
            String key = "earningsDates:" + nt;
            return l2.get(key, new com.fasterxml.jackson.core.type.TypeReference<com.example.yfin.model.earnings.EarningsDatesResponse>() {})
                    .switchIfEmpty(
                            yahoo.getJson(path, "/quote/" + nt)
                                    .map(m -> mapEarningsDates(nt, m))
                                    .flatMap(res -> l2.set(key, res, java.time.Duration.ofSeconds(300)).thenReturn(res))
                    );
        });
    }

    @Cacheable(cacheNames = "earningsDates", key = "#ticker + ':' + #exchange")
    public Mono<EarningsDatesResponse> earningsDatesEx(String ticker, String exchange) {
        String modules = "earnings,calendarEvents";
        return resolver.normalize(ticker, exchange).flatMap(nt -> {
            String path = "/v10/finance/quoteSummary/" + nt + "?modules=" + modules + "&lang=en-US&region=US&corsDomain=finance.yahoo.com";
            String key = "earningsDates:" + nt;
            return l2.get(key, new com.fasterxml.jackson.core.type.TypeReference<com.example.yfin.model.earnings.EarningsDatesResponse>() {})
                    .switchIfEmpty(
                            yahoo.getJson(path, "/quote/" + nt)
                                    .map(m -> mapEarningsDates(nt, m))
                                    .flatMap(res -> l2.set(key, res, java.time.Duration.ofSeconds(300)).thenReturn(res))
                    );
        });
    }

    @Cacheable(cacheNames = "profile", key = "#ticker + ':' + #exchange")
    public Mono<ProfileResponse> profileEx(String ticker, String exchange) {
        String modules = "summaryProfile,esgScores";
        return resolver.normalize(ticker, exchange).flatMap(nt -> {
            String path = "/v10/finance/quoteSummary/" + nt + "?modules=" + modules + "&lang=en-US&region=US&corsDomain=finance.yahoo.com";
            String key = "profile:" + nt;
            return l2.get(key, new com.fasterxml.jackson.core.type.TypeReference<com.example.yfin.model.profile.ProfileResponse>() {})
                    .switchIfEmpty(
                            yahoo.getJson(path, "/quote/" + nt)
                                    .map(m -> mapProfile(nt, m))
                                    .flatMap(res -> enrichProfileWithMetaAndDart(nt, res))
                                    .flatMap(res -> l2.set(key, res, java.time.Duration.ofSeconds(60)).thenReturn(res))
                    );
        });
    }

    private reactor.core.publisher.Mono<com.example.yfin.model.profile.ProfileResponse> enrichProfileWithMetaAndDart(String ticker, com.example.yfin.model.profile.ProfileResponse res) {
        return listingRepo.findById(ticker)
                .map(doc -> {
                    java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("market", doc.getMarket());
                    m.put("name", doc.getName());
                    m.put("sector", doc.getSector());
                    res.setListingMeta(m);
                    return res;
                })
                .defaultIfEmpty(res)
                .flatMap(r -> {
                    // DART 요약(키 없으면 패스)
                    return dart.company("")
                            .onErrorResume(e -> reactor.core.publisher.Mono.empty())
                            .map(map -> {
                                java.util.Map<String, Object> safe = asMap(map);
                                if (safe != null) {
                                    r.setDartSummary(safe);
                                }
                                return r;
                            })
                            .defaultIfEmpty(r);
                });
    }

    private FinancialsResponse mapFinancials(String ticker, Map<String, Object> body) {
        FinancialsResponse response = new FinancialsResponse();
        response.setTicker(ticker);
        Map<String, Object> qs = asMap(body.get("quoteSummary"));
        if (qs == null) return response;
        List<Map<String, Object>> result = asListOfMap(qs.get("result"));
        if (result == null || result.isEmpty()) return response;
        Map<String, Object> r0 = result.get(0);

        Map<String, Object> ish = asMap(r0.get("incomeStatementHistory"));
        Map<String, Object> bsh = asMap(r0.get("balanceSheetHistory"));
        Map<String, Object> cfh = asMap(r0.get("cashflowStatementHistory"));

        FinancialsDto dto = new FinancialsDto();
        // incomeStatementHistory.incomeStatementHistory[0]
        Map<String, Object> is0 = firstMap(ish, "incomeStatementHistory");
        if (is0 != null) {
            dto.setTotalRevenue(extractRawLong(is0.get("totalRevenue")));
            dto.setOperatingIncome(extractRawLong(is0.get("operatingIncome")));
            dto.setNetIncome(extractRawLong(is0.get("netIncome")));
        }
        // balanceSheetHistory.balanceSheetStatements[0]
        Map<String, Object> bs0 = firstMap(bsh, "balanceSheetStatements");
        if (bs0 != null) {
            dto.setTotalAssets(extractRawLong(bs0.get("totalAssets")));
            dto.setTotalLiab(extractRawLong(bs0.get("totalLiab")));
        }
        // cashflowStatementHistory.cashflowStatements[0]
        Map<String, Object> cf0 = firstMap(cfh, "cashflowStatements");
        if (cf0 != null) {
            dto.setTotalCashFromOperatingActivities(extractRawLong(cf0.get("totalCashFromOperatingActivities")));
        }
        response.setSummary(dto);
        return response;
    }

    private EarningsResponse mapEarnings(String ticker, Map<String, Object> body) {
        EarningsResponse response = new EarningsResponse();
        response.setTicker(ticker);
        // summary (quarterly / next earnings date)
        EarningsDatesResponse ed = mapEarningsDates(ticker, body);
        response.setSummary(ed.getSummary());
        // calendar
        response.setCalendar(mapCalendar(ticker, body));
        // trend
        response.setTrend(mapEarningsTrend(body));
        return response;
    }

    public CalendarResponse mapCalendar(String ticker, Map<String, Object> body) {
        CalendarResponse response = new CalendarResponse();
        response.setTicker(ticker);
        Map<String, Object> qs = asMap(body.get("quoteSummary"));
        if (qs == null) return response;
        java.util.List<Map<String, Object>> result = asListOfMap(qs.get("result"));
        if (result == null || result.isEmpty()) return response;
        Map<String, Object> r0 = result.get(0);
        Map<String, Object> ce = asMap(r0.get("calendarEvents"));
        if (ce == null) return response;

        Map<String, Object> earnings = asMap(ce.get("earnings"));
        if (earnings != null) {
            com.example.yfin.model.calendar.EarningsCalendar ec = new com.example.yfin.model.calendar.EarningsCalendar();
            ec.setEarningsDate(toFormattedDate(earnings.get("earningsDate")));
            ec.setEarningsCallDate(toFormattedDate(earnings.get("earningsCallDate")));
            Object est = earnings.get("isEarningsDateEstimate");
            if (est instanceof Boolean b) ec.setEarningsDateEstimate(b);
            ec.setEarningsAverage(extractRawDouble(earnings.get("earningsAverage")));
            ec.setEarningsLow(extractRawDouble(earnings.get("earningsLow")));
            ec.setEarningsHigh(extractRawDouble(earnings.get("earningsHigh")));
            Long ravg = extractRawLong(earnings.get("revenueAverage"));
            Long rlow = extractRawLong(earnings.get("revenueLow"));
            Long rhigh = extractRawLong(earnings.get("revenueHigh"));
            ec.setRevenueAverage(ravg);
            ec.setRevenueLow(rlow);
            ec.setRevenueHigh(rhigh);
            response.setEarnings(ec);
        }

        response.setExDividendDate(toFormattedDate(ce.get("exDividendDate")));
        response.setDividendDate(toFormattedDate(ce.get("dividendDate")));
        return response;
    }

    // --------- Helpers: safe extraction & coercion ---------
    private static Map<String, Object> asMap(Object o) {
        if (!(o instanceof Map<?, ?> m)) return null;
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<?, ?> e : m.entrySet()) {
            if (e.getKey() instanceof String key) {
                out.put(key, e.getValue());
            }
        }
        return out;
    }

    private static java.util.List<Map<String, Object>> asListOfMap(Object o) {
        if (!(o instanceof java.util.List<?> list)) return null;
        java.util.List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (Object item : list) {
            Map<String, Object> m = asMap(item);
            if (m != null) out.add(m);
        }
        return out;
    }

    private static java.util.List<?> asList(Object o) {
        return (o instanceof java.util.List<?> l) ? l : null;
    }

    private static String asString(Object o) {
        return (o == null) ? null : o.toString();
    }

    private static Double extractRawDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        Map<String, Object> m = asMap(value);
        if (m == null) return null;
        Object raw = m.get("raw");
        return (raw instanceof Number n2) ? n2.doubleValue() : null;
    }

    private static Long extractRawLong(Object value) {
        if (value instanceof Number n) return n.longValue();
        Map<String, Object> m = asMap(value);
        if (m == null) return null;
        Object raw = m.get("raw");
        return (raw instanceof Number n2) ? n2.longValue() : null;
    }

    private static Integer extractInteger(Object value) {
        if (value instanceof Number n) return n.intValue();
        Map<String, Object> m = asMap(value);
        if (m != null) {
            Object raw = m.get("raw");
            if (raw instanceof Number n2) return n2.intValue();
        }
        return null;
    }

    private static com.example.yfin.model.common.FormattedDate toFormattedDate(Object value) {
        if (value == null) return null;
        // Accept Map {raw, fmt}
        Map<String, Object> m0 = asMap(value);
        if (m0 != null) {
            com.example.yfin.model.common.FormattedDate fd = new com.example.yfin.model.common.FormattedDate();
            Object raw = m0.get("raw");
            Object fmt = m0.get("fmt");
            if (raw instanceof Number n) fd.setRaw(n.longValue());
            if (fmt != null) fd.setFmt(fmt.toString());
            if (fd.getRaw() == null && fd.getFmt() == null) return null;
            return fd;
        }
        // Accept List<Map> and take first
        java.util.List<?> list = asList(value);
        if (list != null && !list.isEmpty()) {
            return toFormattedDate(list.get(0));
        }
        return null;
    }

    private static Map<String, Object> firstMap(Map<String, Object> parent, String arrayField) {
        if (parent == null) return null;
        Object arr = parent.get(arrayField);
        if (!(arr instanceof java.util.List<?> list) || list.isEmpty()) return null;
        Object first = list.get(0);
        return asMap(first);
    }

    private ProfileResponse mapProfile(String ticker, Map<String, Object> body) {
        ProfileResponse res = new ProfileResponse();
        res.setTicker(ticker);
        Map<String, Object> qs = asMap(body.get("quoteSummary"));
        if (qs == null) return res;
        List<Map<String, Object>> result = asListOfMap(qs.get("result"));
        if (result == null || result.isEmpty()) return res;
        Map<String, Object> r0 = result.get(0);
        Map<String, Object> sp = asMap(r0.get("summaryProfile"));
        if (sp != null) {
            SummaryProfileDto dto = new SummaryProfileDto();
            dto.setIndustry(asString(sp.get("industry")));
            dto.setSector(asString(sp.get("sector")));
            Integer fte = extractInteger(sp.get("fullTimeEmployees"));
            dto.setFullTimeEmployees(fte);
            dto.setPhone(asString(sp.get("phone")));
            dto.setWebsite(asString(sp.get("website")));
            dto.setCountry(asString(sp.get("country")));
            dto.setCity(asString(sp.get("city")));
            dto.setAddress1(asString(sp.get("address1")));
            dto.setLongBusinessSummary(asString(sp.get("longBusinessSummary")));
            res.setSummaryProfile(dto);
        }
        Map<String, Object> esg = asMap(r0.get("esgScores"));
        if (esg != null) {
            EsgScoresDto dto = new EsgScoresDto();
            dto.setTotalEsg(extractRawDouble(esg.get("totalEsg")));
            dto.setEnvironmentalScore(extractRawDouble(esg.get("environmentScore")));
            dto.setSocialScore(extractRawDouble(esg.get("socialScore")));
            dto.setGovernanceScore(extractRawDouble(esg.get("governanceScore")));
            res.setEsgScores(dto);
        }
        return res;
    }

    private EarningsTrendDto mapEarningsTrend(Map<String, Object> body) {
        EarningsTrendDto dto = new EarningsTrendDto();
        Map<String, Object> qs = asMap(body.get("quoteSummary"));
        if (qs == null) return dto;
        List<Map<String, Object>> result = asListOfMap(qs.get("result"));
        if (result == null || result.isEmpty()) return dto;
        Map<String, Object> r0 = result.get(0);
        Map<String, Object> et = asMap(r0.get("earningsTrend"));
        if (et == null) return dto;
        List<Map<String, Object>> trend = asListOfMap(et.get("trend"));
        if (trend == null) return dto;
        for (Map<String, Object> t : trend) {
            String period = asString(t.get("period"));
            Map<String, Object> ee = asMap(t.get("earningsEstimate"));
            Double avg = (ee == null) ? null : extractRawDouble(ee.get("avg"));
            if (period != null) {
                if (period.contains("0q") || period.contains("+1q") || period.equalsIgnoreCase("currentQuarter")) {
                    dto.setNextQuarterEpsEstimate(avg);
                } else if (period.contains("0y") || period.equalsIgnoreCase("currentYear")) {
                    dto.setCurrentYearEpsEstimate(avg);
                } else if (period.contains("+1y") || period.contains("1y") || period.equalsIgnoreCase("nextYear")) {
                    dto.setNextYearEpsEstimate(avg);
                }
            }
        }
        return dto;
    }
}


