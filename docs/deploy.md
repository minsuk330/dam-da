# 배포

백엔드(Spring + PostgreSQL)는 VPS에 Docker Compose로, 프론트엔드(Expo 웹 빌드)는 Vercel에 올린다. 앱은 Vercel 주소에서 `/api/**`를 부르고, Vercel rewrites가 이를 VPS로 넘겨 같은 출처로 동작한다.

```
브라우저 ──▶ Vercel (frontend/dist)
              └─ /api/** ──rewrite──▶ https://hack.refit-100.site ──▶ Caddy(:443) ──▶ app(:8080) ──▶ postgres
Claude 커넥터 ──▶ https://hack.refit-100.site/mcp ─────────────────────┘
```

## 백엔드 (VPS)

구성 파일은 `backend/deploy/`에 있다.

| 파일 | 내용 |
|---|---|
| `compose.yml` | `postgres`(17), `app`(`backend/Dockerfile` 빌드), `caddy`(HTTPS·리버스 프록시). 외부에는 Caddy의 80·443만 연다 |
| `Caddyfile` | `DOMAIN`으로 Let's Encrypt 인증서 자동 발급, `/v3/api-docs`·`/swagger-ui` 차단, `/mcp` 스트리밍을 위해 버퍼링 끔 |
| `.env.example` | 배포용 환경 변수. `.env`로 복사해 채운다(커밋 금지) |

### 준비

- DNS: `hack.refit-100.site`의 A(필요하면 AAAA) 레코드가 VPS IP를 가리킨다.
- 방화벽: 80·443(TCP), 443(UDP, HTTP/3) 허용. 인증서 발급에 80이 필요하다.
- 80·443을 쓰는 다른 웹 서버(nginx 등)가 없어야 한다. 있으면 그 서버에서 `127.0.0.1`의 앱으로 넘기도록 바꾸고 `caddy` 서비스를 뺀다.
- Docker와 Compose 플러그인(`docker compose version`).

### 처음 배포

```bash
git clone https://github.com/minsuk330/ku-hack.git && cd ku-hack/backend/deploy
cp .env.example .env
# .env 채우기: DB_PASSWORD, OPENAI_API_KEY, TYPESAFE_API_KEY, DEV_TOOLS_TOKEN(openssl rand -hex 32)
docker compose up -d --build
docker compose ps                     # app이 healthy가 될 때까지 (첫 기동 1분 안팎)
curl https://hack.refit-100.site/healthz
```

빈 DB로 시작한다. 테이블은 앱이 기동하면서 만든다(`ddl-auto: update`).

### 업데이트

```bash
cd ku-hack && git pull
cd backend/deploy && docker compose up -d --build app
```

### 운영

```bash
docker compose logs -f app                          # 로그
docker compose exec postgres psql -U review review  # DB 접속
docker compose exec -T postgres pg_dump -U review review > backup-$(date +%F).sql   # 백업
docker compose exec -T postgres psql -U review review < backup.sql                  # 복원(빈 DB에)
docker compose down -v                              # 전부 지우고 처음부터(DB·인증서 볼륨 삭제, 되돌릴 수 없음)
```

### 시간 이동 데모 (`/dev/**`)

배포 서버의 `/dev/**`는 `X-Dev-Token` 헤더가 `.env`의 `DEV_TOOLS_TOKEN`과 같을 때만 열린다(그 외 404). 토큰은 프론트엔드 번들이나 화면에 넣지 않고 curl로만 쓴다.

```bash
export DEV_TOOLS_TOKEN=...   # .env와 같은 값
curl -H "X-Dev-Token: $DEV_TOOLS_TOKEN" https://hack.refit-100.site/dev/clock
curl -X POST -H "X-Dev-Token: $DEV_TOOLS_TOKEN" "https://hack.refit-100.site/dev/clock/travel?days=7"
curl -X POST -H "X-Dev-Token: $DEV_TOOLS_TOKEN" https://hack.refit-100.site/dev/clock/reset
```

시간 이동 값은 메모리에만 있어 앱을 재시작하면 0으로 돌아간다.

### 시간대

매일 학습의 "오늘", 연속 학습 일수, 알림 시각은 JVM 기본 시간대를 따른다. compose가 `-Duser.timezone=Asia/Seoul`로 KST를 쓴다(로그 시각 `+09:00`). 실행 이미지에 OS 시간대 데이터가 없어 컨테이너 셸의 `date`는 UTC로 보이지만 앱과는 무관하다.

### Claude 커넥터

커넥터 URL은 `https://hack.refit-100.site/mcp`다. 커넥터 OAuth(#96) 전까지는 인증이 없어 URL을 아는 사람은 데모 사용자에게 학습 대화를 저장할 수 있다. 데모 기간에는 URL을 공개하지 않는다.

### 로그인

- `.env`에 `PUBLIC_SERVER_URL`, `APP_URL`(Vercel 주소), `AUTH_SIGNING_KEY`, `GOOGLE_*`, `KAKAO_*`를 넣는다. `AUTH_SIGNING_KEY`가 비면 재시작할 때마다 모두 로그아웃된다.
- 구글 Cloud Console·Kakao Developers의 redirect URI에 `https://hack.refit-100.site/login/oauth2/code/google`, `.../kakao`를 추가한다.
- `DEV_TOOLS_ENABLED=true`면 앱에 "데모 계정으로 시작"이 열린다(`POST /api/auth/demo`). 누구나 데모 사용자로 들어올 수 있으므로 시연 기간에만 켠다.

## 프론트엔드 (Vercel)

- Root Directory: `frontend`. 빌드 설정은 `frontend/vercel.json`(`npx expo export -p web` → `dist`).
- 환경 변수: `EXPO_PUBLIC_API_MOCK=false`. `EXPO_PUBLIC_API_URL`은 넣지 않는다(비우면 같은 출처 `/api`를 부른다).
- `frontend/vercel.json` rewrites가 `/api/**`를 `https://hack.refit-100.site/api/**`로 넘긴다. 같은 출처라 백엔드 CORS는 열지 않는다(`CORS_ALLOWED_ORIGINS=`).

## 배포 확인 순서

1. `curl https://hack.refit-100.site/healthz` → 200
2. `curl https://hack.refit-100.site/api/auth/options` → 200 JSON(켜진 로그인 제공자). `/api/daily`는 토큰 없이 401
3. `curl -o /dev/null -w "%{http_code}" https://hack.refit-100.site/dev/clock` → 404(토큰 없음), 토큰을 주면 200
4. Vercel 주소에서 `/api/auth/options` → 200(rewrite 경유)
5. Claude에 커넥터 연결 → 대화 저장 → 앱에 새 학습 세션 알림
