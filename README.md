### yfin-java-lite

Yahoo Finance 기반 시세/차트/배당/옵션/재무/검색 API를 제공하는 Spring Boot 3(WebFlux) 경량 서버입니다. 반응형(reactive) 클라이언트(WebClient)와 캐시(Caffeine/Redis), MongoDB(reactive)를 사용합니다.

### 주요 기능
- **시세**: 단일/다중 종목 현재가, 변동, 거래 정보 조회
- **차트**: 기간(range)·간격(interval)별 HLOCV 데이터
- **배당**: 종목별 배당 이력
- **옵션**: 최근/지정 만기 옵션 체인
- **재무/실적/프로필**: 요약 재무제표, 실적/가이던스, 기업 개요/ESG
- **검색**: 티커/뉴스 검색, Google News RSS 대체 엔드포인트 제공

### 요구 사항
- Java 17+
- Gradle Wrapper 포함
- (선택) Redis, MongoDB

### 빌드
```bash
./gradlew clean bootJar
```
- 산출물: `build/libs/yfin-java-lite-0.1.0.jar` (fat JAR, 의존성 포함)

### 실행
```bash
# 기본 포트(8080)
java -jar build/libs/yfin-java-lite-0.1.0.jar

# 포트 지정
java -Dserver.port=8080 -jar build/libs/yfin-java-lite-0.1.0.jar

# 서버 예시(백그라운드)
nohup $JAVA_HOME/bin/java -Dserver.port=8080 -jar /service/yfin-java-lite/yfin-java-lite.jar > /service/yfin-java-lite/nohup.out 2>&1 &
```

### 설정(application.yml)
`src/main/resources/application.yml` 기본값을 사용합니다. 운영 환경에서는 민감 정보(예: MongoDB URI, API 키)를 환경변수/외부 설정으로 분리하는 것을 권장합니다.

- **MongoDB**: `spring.data.mongodb.uri`
- **Redis**: `spring.redis.host`, `spring.redis.port`, `spring.redis.timeout`
- **캐시 TTL**: `cache.quote-ttl-seconds`, `cache.history-ttl-seconds`, `cache.dividends-ttl-seconds`
- **폴백 프로바이더(선택)**: 키가 비어 있으면 폴백은 비활성화되며 기본은 Yahoo입니다.
  - `alphaVantage.apiKey`: Alpha Vantage 키
  - `finnhub.apiKey`: Finnhub 키

### 엔드포인트 요약
- `GET /quote?ticker=...&exchange=`: 단일 종목 시세
- `GET /quotes?tickers=AA,BB&exchange=`: 다중 종목 시세(쉼표 구분)
- `GET /history?ticker=...&range=1mo&interval=1d&autoAdjust=true&exchange=`: 과거 시세
- `GET /dividends?ticker=...&range=5y&exchange=`: 배당 이력
- `GET /options?ticker=...&expiration=&exchange=`: 옵션 체인(만기 epoch seconds)
- `GET /financials?ticker=...&exchange=`: 재무 요약
- `GET /earnings?ticker=...&exchange=`: 실적/가이던스/일정
- `GET /profile?ticker=...&exchange=`: 기업 개요/ESG
- `GET /search?q=...&count=10&lang=&region=`: 검색
- `GET /search/google?q=...&count=10&lang=` 또는 `GET /news/google`(대체 경로): Google News RSS
- `GET /calendar?ticker=...&exchange=`: 캘린더/이벤트(calendarEvents)
- `GET /earnings/dates?ticker=...&exchange=`: 실적발표 일정(과거/미래)
 - `GET /stream/quotes?tickers=AA,BB&exchange=&intervalSec=5`: SSE 실시간 시세 스트림(heartbeat 포함)
 - `GET /screener/filter?...`: 시장/배당/변동성/거래량 필터 스크리너
 - `GET /screener/sector/ranking?...`: 섹터/업종 랭킹
 - `GET /indicators/ma?...`: 이동평균(MA) 시계열
 - `GET /indicators/rsi?...`: RSI 시계열
 - `POST /portfolio/summary`: 포트폴리오 손익/배당 요약
 - `GET /corp-actions?ticker=...`: 기업행위 요약(배당락/지급일/최근 배당/스플릿)

참고: 한국 6자리 숫자 티커(예: 005930)는 자동으로 `.KS`/`.KQ` 접미사를 판별합니다. 필요 시 `exchange` 파라미터로 강제 지정 가능합니다.

### 실시간 WebSocket
- 엔드포인트: `ws://<host>:<port>/ws/quotes?tickers=AAPL,MSFT&intervalSec=1`
- 동작:
  - Finnhub API 키가 설정되어 있으면 실시간 WS 구독(저지연)
  - 키가 없으면 내부 폴링으로 자동 폴백(SSE 대비 유사 레이턴시)
- 메시지 예시(서버→클라이언트):
```json
{"symbol":"AAPL","price":230.49,"dp":0.51}
```
- 빠른 테스트:
```bash
npx -y wscat -c 'ws://localhost:8080/ws/quotes?tickers=AAPL,MSFT&intervalSec=1'
```

### 사용 예시
```