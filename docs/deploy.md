# 배포

백엔드(Spring + PostgreSQL)는 VPS에 Docker Compose로, 프론트엔드(Expo 웹 빌드)는 Vercel에 올린다. 앱은 Vercel 주소에서 `/api/**`를 부르고, Vercel rewrites가 이를 VPS로 넘겨 같은 출처로 동작한다.

```
브라우저 ──▶ Vercel (frontend/dist)
              └─ /api/** ──rewrite──▶ https://hack.refit-100.site ──▶ 호스트 nginx(:443) ──▶ app(127.0.0.1:18080) ──▶ postgres
Claude 커넥터 ──▶ https://hack.refit-100.site/mcp ─────────────────────┘
```

## 백엔드 (VPS)

구성 파일은 `backend/deploy/`에 있다.

| 파일 | 내용 |
|---|---|
| `compose.yml` | `postgres`(17, 외부 포트 없음), `app`(`backend/Dockerfile` 빌드, `127.0.0.1:${APP_PORT}`에만 열림), `caddy`(선택 프로필) |
| `nginx.conf.example` | 호스트 nginx 사이트 예시. `/v3/api-docs`·`/swagger-ui` 차단, `/mcp` 스트리밍을 위해 버퍼링 끔 |
| `Caddyfile` | 80·443이 비어 있는 VPS용(`--profile caddy`). `DOMAIN` 인증서 자동 발급 |
| `.env.example` | 배포용 환경 변수. `.env`로 복사해 채운다(커밋 금지) |

현재 VPS(`85.113.70.112`)는 호스트 nginx가 80·443을 쓰고 다른 서비스도 같이 돌므로 nginx 방식으로 올린다. 앱 포트 `18080`은 다른 서비스(`127.0.0.1:8080` 등)와 겹치지 않게 고른 값이다.

### 준비

- DNS: `hack.refit-100.site`의 A(필요하면 AAAA) 레코드가 VPS IP를 가리킨다.
- 방화벽: 80·443 허용. 인증서 발급에 80이 필요하다.
- Docker와 Compose 플러그인(`docker compose version`), 호스트 nginx와 certbot.

### 처음 배포

```bash
cd ~/apps && git clone https://github.com/minsuk330/ku-hack.git khack && cd khack/backend/deploy
cp .env.example .env
# .env 채우기: DB_PASSWORD, OPENAI_API_KEY, TYPESAFE_API_KEY, DEV_TOOLS_TOKEN(openssl rand -hex 32)
docker compose up -d --build
docker compose ps                     # app이 healthy가 될 때까지 (첫 기동 1분 안팎)

# 호스트 nginx 사이트와 인증서
cp nginx.conf.example /etc/nginx/sites-available/hack.refit-100.site
ln -s /etc/nginx/sites-available/hack.refit-100.site /etc/nginx/sites-enabled/
nginx -t && systemctl reload nginx
certbot --nginx -d hack.refit-100.site --redirect
curl https://hack.refit-100.site/healthz
```

80·443이 비어 있는 VPS라면 nginx 대신 `docker compose --profile caddy up -d --build`로 Caddy가 인증서까지 처리한다.

빈 DB로 시작한다. 테이블은 앱이 기동하면서 만든다(`ddl-auto: update`).

### 업데이트

```bash
cd ~/apps/khack && git pull
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

커넥터 URL은 `https://hack.refit-100.site/mcp`다(OAuth Client ID/Secret 칸은 비운다). Claude에서 "연결"을 누르면 로그인 창(`/login`, 앱과 같은 구글·카카오 계정) → 연결 승인(`/connect/consent`)을 거쳐 연결된다(스펙 §7.9, MCP OAuth 2.1). 저장한 대화는 로그인한 사용자 계정에 들어간다.

- 토큰 서명 키가 `AUTH_SIGNING_KEY`로 고정되어 있어야 재시작 뒤에도 연결이 유지된다. 클라이언트와 갱신 토큰(30일)은 DB(`connector_client`, `connector_authorization`)에 있다.
- `PUBLIC_SERVER_URL`이 실제 공개 주소와 같아야 한다(토큰 `iss`와 메타데이터에 쓰인다).

### 로그인

- `.env`에 `PUBLIC_SERVER_URL`, `APP_URL`(Vercel 주소), `AUTH_SIGNING_KEY`, `GOOGLE_*`, `KAKAO_*`를 넣는다. `AUTH_SIGNING_KEY`가 비면 재시작할 때마다 모두 로그아웃된다. 키는 PKCS#8 DER이어야 한다(`openssl genpkey ... | openssl pkcs8 -topk8 -nocrypt -outform DER | base64 | tr -d '\n'`). OpenSSL 3.0.x의 `genpkey -outform DER`는 PKCS#1로 내보내 기동이 실패한다(`Unable to decode key`).
- 구글 Cloud Console·Kakao Developers의 redirect URI에 `https://hack.refit-100.site/login/oauth2/code/google`, `.../kakao`를 추가한다.
- `DEV_TOOLS_ENABLED=true`면 앱에 "데모 계정으로 시작"이 열린다(`POST /api/auth/demo`). 누구나 데모 사용자로 들어올 수 있으므로 시연 기간에만 켠다.

## 프론트엔드 (Vercel)

- 프로젝트 `khack-frontend`가 GitHub `minsuk330/ku-hack`에 연결돼 있다. `main`에 merge되면 production, 다른 브랜치·PR은 preview로 자동 배포된다. `frontend/`가 바뀌지 않은 커밋은 `ignoreCommand`로 빌드를 건너뛴다.
- Root Directory: `frontend`. 빌드 설정은 `frontend/vercel.json`(`EXPO_PUBLIC_API_MOCK=false npx expo export -p web` → `dist`). mock 끄기는 빌드 명령에 들어 있어 Vercel 환경 변수로 넣지 않아도 된다.
- `EXPO_PUBLIC_API_URL`은 넣지 않는다(비우면 같은 출처 `/api`를 부른다).
- 손으로 배포할 때는 레포 루트에서 `vercel deploy`를 실행한다(Root Directory가 `frontend`라 `frontend/`에서 실행하면 경로가 겹친다).
- `frontend/vercel.json` rewrites가 `/api/**`를 `https://hack.refit-100.site/api/**`로 넘긴다. 같은 출처라 백엔드 CORS는 열지 않는다(`CORS_ALLOWED_ORIGINS=`).

## 배포 확인 순서

1. `curl https://hack.refit-100.site/healthz` → 200
2. `curl https://hack.refit-100.site/api/auth/options` → 200 JSON(켜진 로그인 제공자). `/api/daily`는 토큰 없이 401
3. `curl -o /dev/null -w "%{http_code}" https://hack.refit-100.site/dev/clock` → 404(토큰 없음), 토큰을 주면 200
4. Vercel 주소에서 `/api/auth/options` → 200(rewrite 경유)
5. Claude에 커넥터 연결 → 대화 저장 → 앱에 새 학습 세션 알림
