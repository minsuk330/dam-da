# 배포

백엔드(Spring + PostgreSQL)는 서버(Azure VM, RAM 1GiB)에 Docker Compose로, 프론트엔드(Expo 웹 빌드)는 Vercel에 올린다. 앱은 Vercel 주소에서 `/api/**`를 부르고, Vercel rewrites가 이를 서버로 넘겨 같은 출처로 동작한다. 백엔드 이미지는 GitHub Actions가 빌드해 GHCR에 올리고 서버는 받아서 실행만 한다.

```
브라우저 ──▶ Vercel (frontend/dist)
              └─ /api/** ──rewrite──▶ https://hack.refit-100.site ──▶ caddy(:443) ──▶ app(:8080) ──▶ postgres
Claude 커넥터 ──▶ https://hack.refit-100.site/mcp ──────────────────┘

main push(backend/**) ──▶ GitHub Actions(backend-image) ──▶ ghcr.io/minsuk330/ku-hack-backend ──pull──▶ 서버
```

## 백엔드 (서버)

구성 파일은 `backend/deploy/`에 있다.

| 파일 | 내용 |
|---|---|
| `compose.yml` | `postgres`(17, 외부 포트 없음), `app`(`APP_IMAGE` 이미지, 비우면 `backend/Dockerfile` 빌드, `127.0.0.1:${APP_PORT}`에만 열림), `caddy`(선택 프로필) |
| `nginx.conf.example` | 호스트 nginx 사이트 예시. `/v3/api-docs`·`/swagger-ui` 차단, `/mcp` 스트리밍을 위해 버퍼링 끔 |
| `Caddyfile` | 80·443이 비어 있는 서버용(`--profile caddy`). `DOMAIN` 인증서 자동 발급 |
| `compose.override.yml` | 서버별 설정(커밋하지 않음). 아래 "작은 서버" 참고 |
| `.env.example` | 배포용 환경 변수. `.env`로 복사해 채운다(커밋 금지) |

현재 서버(Azure VM, 2026-10-05 VPS에서 이전)는 khack만 돌리므로 Caddy 방식으로 올린다. 다른 서비스와 같이 쓰는 서버라면 호스트 nginx(`nginx.conf.example`) 방식을 쓴다.

### 준비

- DNS: `hack.refit-100.site`의 A(필요하면 AAAA) 레코드가 서버 IP를 가리킨다(Vercel DNS).
- 방화벽: 80·443 허용. 인증서 발급에 80이 필요하다.
- Docker와 Compose 플러그인(`docker compose version`). nginx 방식이면 호스트 nginx와 certbot.
- GHCR 패키지 `ku-hack-backend`가 public이어야 서버가 로그인 없이 받는다. private이면 서버에서 `docker login ghcr.io`(`read:packages` 토큰)를 먼저 한다.

### 처음 배포

```bash
cd ~/apps && git clone https://github.com/minsuk330/ku-hack.git khack && cd khack/backend/deploy
cp .env.example .env
# .env 채우기: DB_PASSWORD, OPENAI_API_KEY, TYPESAFE_API_KEY, DEV_TOOLS_TOKEN(openssl rand -hex 32)
docker compose pull app
docker compose --profile caddy up -d  # Caddy가 인증서까지 받는다
docker compose ps                     # app이 healthy가 될 때까지 (첫 기동 1분 안팎)
curl https://hack.refit-100.site/healthz
```

호스트 nginx를 쓰는 서버라면 `--profile caddy` 없이 올리고 nginx 사이트와 인증서를 붙인다.

```bash
cp nginx.conf.example /etc/nginx/sites-available/hack.refit-100.site
ln -s /etc/nginx/sites-available/hack.refit-100.site /etc/nginx/sites-enabled/
nginx -t && systemctl reload nginx
certbot --nginx -d hack.refit-100.site --redirect
curl https://hack.refit-100.site/healthz
```

빈 DB로 시작한다. 테이블은 앱이 기동하면서 만든다(`ddl-auto: update`).

### 작은 서버 (RAM 1GiB)

swap 2GiB를 켜고, `backend/deploy/compose.override.yml`로 메모리를 줄인다. compose가 자동으로 합친다.

```yaml
services:
  app:
    environment:
      JAVA_TOOL_OPTIONS: -Xmx400m -Xss512k -XX:MaxMetaspaceSize=192m -XX:+UseSerialGC -Duser.timezone=Asia/Seoul
  postgres:
    command: ["postgres", "-c", "shared_buffers=64MB", "-c", "max_connections=30"]
```

### 업데이트

```bash
# main에 backend/** 변경이 merge되면 Actions(backend-image)가 이미지를 올린다. 끝난 뒤:
cd ~/apps/khack && git pull           # compose·Caddyfile 변경 반영
cd backend/deploy && docker compose pull app && docker compose --profile caddy up -d
docker image prune -f                 # 이전 이미지 정리(디스크)
```

특정 커밋으로 되돌리려면 `.env`의 `APP_IMAGE`를 `ghcr.io/minsuk330/ku-hack-backend:sha-<7자리>`로 바꾸고 같은 명령을 실행한다. Actions 없이 서버에서 직접 빌드하려면 `APP_IMAGE`를 비우고 `docker compose up -d --build app`(RAM 2GiB 이상 권장).

### 운영

```bash
docker compose logs -f app                          # 로그
docker compose exec postgres psql -U review review  # DB 접속
docker compose exec -T postgres pg_dump -U review review > backup-$(date +%F).sql   # 백업
docker compose exec -T postgres psql -U review review < backup.sql                  # 복원(빈 DB에)
docker compose down -v                              # 전부 지우고 처음부터(DB·인증서 볼륨 삭제, 되돌릴 수 없음)
```

### 시간 이동 데모 (`/dev/**`)

배포 서버의 `/dev/**`는 `X-Dev-Token` 헤더가 `.env`의 `DEV_TOOLS_TOKEN`과 같을 때만 열린다(그 외 404). 토큰은 코드·프론트엔드 번들에 넣지 않는다. Vercel은 `/dev/**`를 넘기지 않으므로 서버 주소로 직접 호출한다.

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

### 토스 인앱 (앱인토스, #149)

토스 미니앱은 출처가 `https://damda-ai.apps.tossmini.com`(콘솔 QR 테스트는 `private-apps`)라 CORS를 열고, 토스 로그인은 서버가 mTLS 인증서로 앱인토스 API를 부른다.

```bash
# 인증서: 앱인토스 콘솔에서 받은 PEM 두 개. 커밋하지 않는다.
mkdir -p ~/apps/khack/backend/certs/toss
scp damda_public.crt damda_private.key <서버>:apps/khack/backend/certs/toss/
# 컨테이너 사용자(pwuser, uid 1001)만 읽게 한다. compose가 /app/certs/toss에 읽기 전용으로 붙인다.
sudo chown -R 1001:1001 ~/apps/khack/backend/certs/toss
sudo chmod 500 ~/apps/khack/backend/certs/toss && sudo chmod 400 ~/apps/khack/backend/certs/toss/*
```

`.env`에 `CORS_ALLOWED_ORIGINS`, `TOSS_MTLS_CERT`, `TOSS_MTLS_KEY`, `TOSS_USER_KEY_SECRET`(`openssl rand -base64 32`), `TOSS_UNLINK_BASIC_AUTH`를 `.env.example`처럼 넣고 `docker compose --profile caddy up -d`. 인증서 파일이 없는데 경로만 넣으면 앱이 뜨지 않는다(PEM을 기동 때 읽는다).

확인: 가짜 코드는 401이고, 로그(`docker compose logs app | grep toss-login`)에 `invalid_grant`가 보이면 mTLS 연결까지 된 것이다. `4050`이면 콘솔의 토스 로그인 설정이 빠진 것이다.

```bash
curl -s -o /dev/null -w "%{http_code}\n" -X POST -H 'Content-Type: application/json' \
  -d '{"authorizationCode":"fake","referrer":"SANDBOX"}' https://hack.refit-100.site/api/auth/toss
```

연결 끊기 콜백: 사용자가 토스 앱에서 로그인 연결을 끊으면 토스가 `https://hack.refit-100.site/toss/unlink`를 부르고, 서버는 그 사용자의 데이터를 모두 지운다(출시 가이드). 콘솔 토스 로그인 설정에 이 URL과 Basic Auth 값(`TOSS_UNLINK_BASIC_AUTH`와 같은 값)을 등록한다. GET·POST 둘 다 받는다. 앱에서 회원 탈퇴하면 서버가 토스 연결도 끊는다(`remove-by-user-key`).

`TOSS_USER_KEY_SECRET`은 바꾸면 기존 토스 사용자를 찾지 못해 새 계정이 생긴다. 백업해 두고 바꾸지 않는다.

### 로그인

- `.env`에 `PUBLIC_SERVER_URL`, `APP_URL`(Vercel 주소), `AUTH_SIGNING_KEY`, `GOOGLE_*`, `KAKAO_*`를 넣는다. `AUTH_SIGNING_KEY`가 비면 재시작할 때마다 모두 로그아웃된다. 키는 PKCS#8 DER이어야 한다(`openssl genpkey ... | openssl pkcs8 -topk8 -nocrypt -outform DER | base64 | tr -d '\n'`). OpenSSL 3.0.x의 `genpkey -outform DER`는 PKCS#1로 내보내 기동이 실패한다(`Unable to decode key`).
- 구글 Cloud Console·Kakao Developers의 redirect URI에 `https://hack.refit-100.site/login/oauth2/code/google`, `.../kakao`를 추가한다.

## 프론트엔드 (Vercel)

- 프로젝트 `khack-frontend`가 GitHub `minsuk330/ku-hack`에 연결돼 있다. `main`에 merge되면 production, 다른 브랜치·PR은 preview로 자동 배포된다. `frontend/`가 바뀌지 않은 커밋은 `ignoreCommand`로 빌드를 건너뛴다.
- Root Directory: `frontend`. 빌드 설정은 `frontend/vercel.json`(`EXPO_PUBLIC_API_MOCK=false npx expo export -p web` → `dist`). mock 끄기는 빌드 명령에 들어 있어 Vercel 환경 변수로 넣지 않아도 된다.
- `EXPO_PUBLIC_API_URL`은 넣지 않는다(비우면 같은 출처 `/api`를 부른다).
- 손으로 배포할 때는 레포 루트에서 `vercel deploy`를 실행한다(Root Directory가 `frontend`라 `frontend/`에서 실행하면 경로가 겹친다).
- `frontend/vercel.json` rewrites가 `/api/**`를 `https://hack.refit-100.site/api/**`로 넘긴다. 같은 출처라 웹에는 백엔드 CORS가 필요 없다(토스 인앱은 위 "토스 인앱" 참고).

## 배포 확인 순서

1. `curl https://hack.refit-100.site/healthz` → 200
2. `curl https://hack.refit-100.site/api/auth/options` → 200 JSON(켜진 로그인 제공자). `/api/daily`는 토큰 없이 401
3. `curl -o /dev/null -w "%{http_code}" https://hack.refit-100.site/dev/clock` → 404(토큰 없음), 토큰을 주면 200
4. Vercel 주소에서 `/api/auth/options` → 200(rewrite 경유)
5. Claude에 커넥터 연결 → 대화 저장 → 앱에 새 학습 세션 알림
