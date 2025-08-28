### yfin-java-lite API 문서

기본 URL: http://localhost:8080

Swagger UI: http://localhost:8080/swagger-ui.html (모델/스키마와 예제는 Swagger에서 확인 가능)

### 공통
- 티커 자동 보정: 한국 6자리 숫자는 `.KS`/`.KQ` 자동 판별. 필요 시 `exchange`로 강제 지정.

### 데이터 소스 및 폴백
- 기본 데이터 소스는 Yahoo Finance입니다.
- Alpha Vantage / Finnhub API 키가 없으면 폴백은 비활성화되고 Yahoo만 사용됩니다.
- 키가 설정되면 Yahoo가 차단/비가용 시 자동으로 폴백을 시도합니다.

설정(application.yml):
```yaml
alphaVantage:
  apiKey: ""
finnhub:
  apiKey: ""
```
환경변수 예시:
```bash
export ALPHAVANTAGE_APIKEY=your_alpha_vantage_key
export FINNHUB_APIKEY=your_finnhub_key
```

### 시세/차트/배당/옵션/검색 (요약)
- GET `/quote?ticker=...&exchange=`
- GET `/quotes?tickers=AA,BB&exchange=`
- GET `/history?ticker=...&range=1mo&interval=1d&autoAdjust=true&exchange=`
- GET `/dividends?ticker=...&range=5y&exchange=`
- GET `/options?ticker=...&expiration=&exchange=`
- GET `/search?q=...&count=10&lang=&region=`
- GET `/search/google?q=...&count=10&lang=` (대체: `/news/google`)

### 재무 요약
- GET `/financials?ticker=...&exchange=`
  - 응답: `FinancialsResponse`
    - `ticker`: string
    - `summary`: `FinancialsDto`
      - `totalRevenue`: Long (최근 매출)
      - `operatingIncome`: Long (최근 영업이익)
      - `netIncome`: Long (최근 순이익)
      - `totalAssets`: Long (최근 총자산)
      - `totalLiab`: Long (최근 총부채)
      - `totalCashFromOperatingActivities`: Long (최근 영업현금흐름)

### 실적/가이던스/일정
- GET `/earnings?ticker=...&exchange=`
  - 응답: `EarningsResponse`
    - `ticker`: string
    - `summary`: `EarningsSummary`
      - `quarterly[]`: `QuarterlyEarnings` { `date`: string, `actual`: Double, `estimate`: Double }
      - `currentQuarterEstimate`: Double
      - `currentQuarterEstimateDate`: string (예: "3Q")
      - `currentQuarterEstimateYear`: int
      - `nextEarningsDate`: `FormattedDate` { `raw`: Long(초), `fmt`: string }
    - `trend`: `EarningsTrendDto` { `nextQuarterEpsEstimate`, `currentYearEpsEstimate`, `nextYearEpsEstimate` }
    - `calendar`: `CalendarResponse` (아래 캘린더 참조)

### 캘린더/이벤트
- GET `/calendar?ticker=...&exchange=`
  - 응답: `CalendarResponse`
    - `ticker`: string
    - `earnings`: `EarningsCalendar`
      - `earningsDate`: `FormattedDate` (다음 실적 발표일, 추정 가능)
      - `earningsCallDate`: `FormattedDate` (실적 콜)
      - `earningsDateEstimate`: boolean (실적일 추정 여부)
      - `earningsAverage/Low/High`: Double (EPS 컨센서스)
      - `revenueAverage/Low/High`: Long (매출 컨센서스)
    - `exDividendDate`: `FormattedDate` (배당락)
    - `dividendDate`: `FormattedDate` (배당 지급)

### 실적 발표 일정(과거/미래)
- GET `/earnings/dates?ticker=...&exchange=`
  - 응답: `EarningsDatesResponse`
    - `ticker`: string
    - `summary`: `EarningsSummary` (구성은 위 `EarningsResponse.summary`와 동일)

### 기업 개요/ESG
- GET `/profile?ticker=...&exchange=`
  - 응답: `ProfileResponse`
    - `ticker`: string
    - `summaryProfile`: `SummaryProfileDto`
      - `industry`, `sector`, `fullTimeEmployees`, `phone`, `website`, `country`, `city`, `address1`, `longBusinessSummary`
    - `esgScores`: `EsgScoresDto`
      - `totalEsg`, `environmentalScore`, `socialScore`, `governanceScore`
    - `listingMeta`: object (시장/섹터/종목명 등 내부 메타, 선택적)
    - `dartSummary`: object (DART 보조 정보, 선택적)

### 샘플 호출
```bash
curl 'http://localhost:8080/calendar?ticker=AAPL'
curl 'http://localhost:8080/earnings/dates?ticker=AAPL'
curl 'http://localhost:8080/calendar?ticker=005930'
curl 'http://localhost:8080/earnings/dates?ticker=005930'
curl 'http://localhost:8080/financials?ticker=AAPL'
curl 'http://localhost:8080/profile?ticker=AAPL'
```

### 주의
- 데이터는 Yahoo Finance 비공식 엔드포인트 기반으로 변동 가능성이 있습니다.
- 일부 필드는 추정치이며(`earningsDateEstimate`), 국가/시장에 따라 제공되지 않을 수 있습니다.


### 실시간 스트림(SSE)
- GET `/stream/quotes?tickers=AAPL,MSFT&exchange=&intervalSec=5`
  - 설명: `intervalSec` 간격으로 다중 종목 시세를 이벤트 스트림으로 전송
  - 이벤트: `heartbeat`(주기적 핑), `quote`(실데이터)
  - 데이터: `QuoteDto`

### 실시간 WebSocket
- WS `/ws/quotes?tickers=AAPL,MSFT&intervalSec=1`
  - Finnhub 키가 설정되면 WS 실시간 트레이드 구독(저지연)
  - 키가 없으면 내부 폴링으로 자동 폴백
  - 응답 메시지(단순화): `{ "symbol": string, "price": number, "dp": number }`

### 스크리너/랭킹
- GET `/screener/filter?market=KS&minDividendYield=0.02&minVolatilityPct=0.5&minVolume=100000`
  - 설명: 시장·배당수익률·근사 변동성(%)·최소 거래량 필터
- GET `/screener/sector/ranking?market=KS&topN=5&sortBy=volume`
  - 설명: 섹터/업종별 상위 N 종목 랭킹(거래대금/변동률 등 기준)

### 기술적 지표
- GET `/indicators/ma?ticker=AAPL&range=6mo&interval=1d&autoAdjust=true&window=20`
  - 응답: `MaPoint[]` { `time`: Instant, `value`: Double }
- GET `/indicators/rsi?ticker=AAPL&range=3mo&interval=1d&autoAdjust=true&window=14`
  - 응답: `RsiPoint[]` { `time`: Instant, `value`: Double }

### 포트폴리오 요약
- POST `/portfolio/summary`
  - 바디: `PositionDto[]` { `symbol`: string, `quantity`: Double, `averageCost`: Double }
  - 응답: `PortfolioSummary`
    - `marketValue`, `cost`, `pnl`, `pnlRate`, `dividendAnnual`, `dividendYield`
    - `items[]` { `symbol`, `quantity`, `price`, `marketValue`, `cost`, `pnl`, `pnlRate`, `dividendAnnual`, `dividendYield` }

### 기업행위 요약
- GET `/corp-actions?ticker=...&exchange=`
  - 응답: `CorpActionsResponse`
    - `ticker`: string
    - `exDividendDate`, `dividendDate`: `FormattedDate`
    - `recentDividends`: `DivRow[]` { `date`: Instant, `amount`: Double }
    - `recentSplits`: `string[]` (예: `yyyy-MM-dd: 4:1`)

### 샘플 호출(추가)
```bash
curl -N 'http://localhost:8080/stream/quotes?tickers=AAPL,MSFT&intervalSec=5'
curl 'http://localhost:8080/screener/filter?market=KS&minDividendYield=0.01&minVolatilityPct=0.5&minVolume=100000'
curl 'http://localhost:8080/screener/sector/ranking?market=KS&topN=5&sortBy=volume'
curl 'http://localhost:8080/indicators/ma?ticker=AAPL&range=6mo&interval=1d&window=20'
curl 'http://localhost:8080/indicators/rsi?ticker=AAPL&range=3mo&interval=1d&window=14'
curl -H 'Content-Type: application/json' -d '[{"symbol":"AAPL","quantity":10,"averageCost":190.5}]' 'http://localhost:8080/portfolio/summary'
curl 'http://localhost:8080/corp-actions?ticker=AAPL'
```


