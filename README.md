### yfin-java-lite

Yahoo Finance 기반 시세/차트/배당/옵션/재무/검색 API를 제공하는 Spring Boot 3(WebFlux) 경량 서버입니다. 반응형(reactive) 클라이언트(WebClient)와 캐시(Caffeine/Redis), MongoDB(reactive)를 사용하며, KIS(한국투자증권) WebSocket을 통한 실시간 데이터 스트리밍을 지원합니다.

### 주요 기능
- **시세**: 단일/다중 종목 현재가, 변동, 거래 정보 조회
- **차트**: 기간(range)·간격(interval)별 HLOCV 데이터
- **배당**: 종목별 배당 이력
- **옵션**: 최근/지정 만기 옵션 체인
- **재무/실적/프로필**: 요약 재무제표, 실적/가이던스, 기업 개요/ESG
- **검색**: 티커/뉴스 검색, Google News RSS 대체 엔드포인트 제공
- **실시간 WebSocket**: KIS WebSocket을 통한 실시간 주식 데이터 스트리밍
- **멀티 클라이언트 지원**: 여러 클라이언트가 동일한 심볼을 구독할 수 있는 팬아웃 구조

### 요구 사항
- Java 17+
- Gradle Wrapper 포함
- Redis (KIS WebSocket 토큰 관리용)
- MongoDB (선택사항)

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

- MongoDB: `spring.data.mongodb.uri`
- Redis: `spring.redis.host`, `spring.redis.port`, `spring.redis.timeout`
- 캐시 TTL: `cache.quote-ttl-seconds`, `cache.history-ttl-seconds`, `cache.dividends-ttl-seconds`
- 폴백 프로바이더(선택): 키가 비어 있으면 폴백은 비활성화되며 기본은 Yahoo입니다.
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
- `GET /corp-actions?ticker=...`: 기업행위 요약(배당락/지급일/스플릿)

참고: 한국 6자리 숫자 티커(예: 005930)는 자동으로 `.KS`/`.KQ` 접미사를 판별합니다. 필요 시 `exchange` 파라미터로 강제 지정 가능합니다.

### 실시간 WebSocket (KIS 우선)
- 엔드포인트: `ws://<host>:<port>/ws/quotes?tickers=AAPL,005930&intervalSec=1`
- **아키텍처**: 단일 KIS WebSocket 연결을 통한 멀티 클라이언트 팬아웃 구조
- **라우팅/동작 순서(우선순위)**
  - KIS 승인키 유효 시 KIS WS로 우선 구독(국내/해외 모두), 실패 시 Finnhub WS 폴백
  - 보강 스냅샷(REST)은 중복 제거 후 병합됨
    - KIS 경로: 최소 1초(`intervalSec`) 보강
    - 폴백 경로: 최소 10초 보강(차단 리스크 완화)
- **요청 파라미터**
  - `tickers`: 쉼표 구분 멀티 심볼. 한국 6자리 티커 자동 `.KS/.KQ` 보정
  - `intervalSec`: 보강 스냅샷 최소 간격(기본 2, KIS 경로 1초까지 허용)
- **응답 메시지(서버→클라이언트)**:
```json
{"symbol":"AAPL","price":230.49,"dp":0.51}
```
- **멀티 클라이언트 지원**: 여러 클라이언트가 동일한 심볼을 구독할 때 모든 클라이언트가 동일한 데이터를 받음
- **Graceful Shutdown**: 애플리케이션 종료 시 KIS 서버에 구독 해제 요청(`tr_type=2`) 전송
- **빠른 테스트**:
```bash
npx -y wscat -c 'ws://localhost:8080/ws/quotes?tickers=005930,AAPL&intervalSec=1'
```

#### KIS 설정(application.yml)
```yaml
api:
  kis:
    appKey: <APP_KEY>
    app-secret: <APP_SECRET>
    access-token-generate-url: https://openapi.koreainvestment.com:9443/oauth2/tokenP
    user-id: <USER_ID>
    pw: <PASSWORD>
    ws-url: wss://openapi.koreainvestment.com:9443/websocket  # 실전 도메인
    approval-url: /oauth2/Approval
    ws-enabled: true  # KIS WebSocket 활성화/비활성화 플래그
    max-symbols-per-session: 41  # KIS WebSocket 세션당 최대 구독 종목 수
```

#### Redis 저장(요약)
- **REST 토큰**: `RestKisToken:<access_token>` 해시(만료 TTL 포함) — 신규 발급 시 기존 `RestKisToken:*` 전부 삭제 후 단일 키만 유지
- **WS 승인키**: `SocketKisToken:<approval_key>` 해시(기본 TTL 24h) — 애플리케이션 시작 시 기존 승인키 삭제 후 새로 발급

#### KIS WebSocket 아키텍처
- **단일 연결**: 하나의 app_key에 하나의 WebSocket 연결만 허용
- **멀티 클라이언트 팬아웃**: 여러 클라이언트가 동일한 심볼을 구독할 때 모든 클라이언트가 동일한 데이터를 받음
- **참조 카운팅**: 각 심볼별로 구독자 수를 추적하여 마지막 구독자가 해제될 때만 KIS 서버에 구독 해제 요청 전송
- **LRU 구독 관리**: 41개 종목 제한 초과 시 가장 오래된 구독을 자동으로 해제하고 새로운 구독을 추가
- **Graceful Shutdown**: 애플리케이션 종료 시 모든 구독된 심볼에 대해 순차적으로 구독 해제 요청(`tr_type=2`) 전송

### 개발 가이드
- **공통 유틸**: `SymbolUtils` — TR ID/키 정규화. `ExchangeSuffix`, `KisTrId` enum 사용
- **Enum**: `OptionType`, `ExchangeSuffix`, `ScreenerSortBy`, `KisTrId`
- **Lombok**: `@RequiredArgsConstructor`로 생성자 최소화, 필요 시 `@Slf4j` 권장
- **문서**: 상세 API 스키마/예제는 `docs/API.md` 및 Swagger UI 참고
- **KIS WebSocket 컴포넌트**:
  - `KisWsClient`: KIS WebSocket 연결 및 데이터 수신 관리
  - `KisWebSocketManager`: 클라이언트별 구독 관리 및 데이터 팬아웃
  - `KisAuthClient`: KIS API 인증 및 토큰 관리
  - `KisWebSocketRequest`: KIS WebSocket 요청 DTO (구독/해제)

### 사용 예시
```bash
# REST API 예시
curl 'http://localhost:8080/quote?ticker=AAPL'
curl 'http://localhost:8080/options?ticker=AAPL'

# WebSocket 예시 (여러 클라이언트가 동일한 심볼 구독 가능)
npx -y wscat -c 'ws://localhost:8080/ws/quotes?tickers=005930,AAPL&intervalSec=1'
```

### 서버 관리
#### Graceful Shutdown 스크립트
```bash
#!/bin/bash
APP_NAME="yfin-java-lite"
GRACEFUL_TIMEOUT=30

# 프로세스 ID 찾기
PID=$(ps -ef | grep "$APP_NAME" | grep -v grep | awk '{print $2}')

if [ -z "$PID" ]; then
    echo "Application '$APP_NAME' is not running"
    exit 0
fi

echo "Found '$APP_NAME' with PID: $PID"

# Graceful shutdown
echo "Initiating graceful shutdown..."
kill -TERM $PID

# 대기 및 상태 확인
for i in $(seq 1 $GRACEFUL_TIMEOUT); do
    if ! kill -0 $PID 2>/dev/null; then
        echo "Application stopped gracefully after ${i} seconds"
        exit 0
    fi
    printf "\rWaiting for graceful shutdown... %d/%d" $i $GRACEFUL_TIMEOUT
    sleep 1
done

echo ""
echo "Graceful shutdown timeout reached, forcing termination..."
kill -9 $PID 2>/dev/null

# 최종 확인
sleep 2
if kill -0 $PID 2>/dev/null; then
    echo "ERROR: Failed to stop application"
    exit 1
else
    echo "Application stopped successfully"
    exit 0
fi
```