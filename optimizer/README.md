# optimizer

개인 FSRS 매개변수 학습·검증 배치 (스펙 §6.4.9, #24). 서버 스케줄러는 java-fsrs이고, java-fsrs에는 매개변수 최적화가 없어 py-fsrs(`fsrs[optimizer]`) 옵티마이저를 쓴다. 적용·재계산은 백엔드가 한다(#25).

## 실행

```bash
cd optimizer
uv run pytest                                              # 테스트 (서버·키 불필요)
uv run khack-optimizer --synthetic                         # 합성 기록으로 학습·검증 결과 확인 (저장 안 함)
uv run khack-optimizer --server http://localhost:8080 --dry-run
uv run khack-optimizer --server http://localhost:8080      # 개선되면 새 매개변수 버전 저장 (--activate면 바로 적용)
uv run khack-optimizer --server http://localhost:8080 --seed-synthetic-user   # 개인화 시연용 합성 사용자 준비
```

서버는 `DEV_TOOLS_ENABLED=true`로 떠 있어야 한다. 원격 서버면 `DEV_TOOLS_TOKEN` 환경 변수를 `X-Dev-Token`으로 보낸다.
대상 사용자는 서버의 현재 사용자다.

## 흐름

1. `GET /dev/review-logs.json` — 등급이 정해진 복습 기록(보류 제외). 같은 날 재확인도 들어 있다.
2. `GET /dev/fsrs-parameters/active` — 지금 적용 중인 매개변수(비교 기준).
3. 시간순으로 앞 `train-ratio`(기본 80%) 기록만 옵티마이저에 준다. 미래 기록이 학습에 들어가지 않는다.
4. 뒤 구간 복습마다 복습 직전 예측 R과 실제 기억 여부(Again이면 0)로 log loss와 RMSE(bins, 20구간)를 계산한다.
   항목 기록은 처음부터 다시 적용하고, 같은 날 재확인은 상태에만 반영하고 채점하지 않는다(옵티마이저 손실과 같은 기준). 재확인 건수는 검증 결과에 따로 남긴다.
5. 학습 매개변수의 log loss가 **기본값과 현재 값 둘 다**보다 `min-improvement`(기본 2%) 이상 낮을 때만 `POST /dev/fsrs-parameters`로 새 버전(`OPTIMIZED`)과 검증 결과를 저장한다.
   그렇지 않으면 기존 값을 유지한다. 저장만 하고 적용은 하지 않는다.

## 적용과 롤백

```bash
curl localhost:8080/dev/fsrs-parameters                      # 저장된 버전 목록
curl -X POST localhost:8080/dev/fsrs-parameters/2/activate   # 현재 사용자에게 버전 2 적용 + 기억 상태 재계산
curl -X POST localhost:8080/dev/fsrs-parameters/1/activate   # 기본값으로 롤백
```

java-fsrs에는 재스케줄이 없으므로, 적용할 때 항목마다 초기 등급(대화 근거, `memory_state.initial_rating`)과 등급 기록을 시간순으로 새 스케줄러에 다시 적용한다.
이후 등급은 새 버전으로 반영되고 복습 기록에 버전이 남는다. 매일 학습 큐는 다시 계산된 다음 복습 시각을 그대로 쓴다.
초기 등급 저장 이전에 초기 평가를 받은 항목은 처음부터 다시 적용할 수 없어 기존 상태를 둔다(응답의 `skipped`).

## 설정값

| 인자 | 기본 | 의미 |
|---|---|---|
| `--min-reviews` | 512 | 전체 등급 기록 최소 수 |
| `--train-ratio` | 0.8 | 시간순 학습 구간 비율 |
| `--min-improvement` | 0.02 | 기본·현재 대비 log loss 상대 개선 기준 |
| `--min-validation-reviews` | 30 | 검증 구간 채점 복습 최소 수 |
| `--epochs` | 20 | 옵티마이저 epoch 수. py-fsrs 기본 5는 합성 기록에서 덜 수렴했다(검증 5.5% vs 20에서 6.8%, 곡선 방향도 반대) |

py-fsrs 옵티마이저는 학습 구간 채점 복습(첫 복습·같은 날 재확인 제외)이 512개(mini batch)보다 적으면 학습 없이 기본값을 돌려준다. 그래서 이 조건도 따로 검사한다. 최소 기록 수 기본값은 그 하한과 같은 512로 둬서 앱 진행도가 512에 닿으면 학습을 시도한다. 첫 복습·재확인·검증 구간을 빼면 학습 구간이 512개에 못 미칠 수 있고, 그때는 기존 매개변수를 유지한다. 실제 개인화는 대략 700~1000개부터 된다.

실행 주기는 정해 두지 않는다(수동 실행). 운영한다면 하루 한 번 정도가 적당하다. 기록이 크게 늘지 않으면 결과도 거의 같다.

## 버전 고정

`fsrs[optimizer]==6.1.1`. java-fsrs 1.0.0과 기본값·계산이 같은 마지막 버전이다(6.2부터 기본값이 바뀐다).
`tests/fixtures/java_fsrs_golden.json`의 복습 순서별 S·D·R·due를 py-fsrs(`tests/test_compat.py`)와 java-fsrs(backend `FsrsGoldenTest`)가 함께 확인한다.
어느 쪽이든 올리면 이 파일을 새 java-fsrs로 다시 만들고 두 테스트를 통과시킨다.

## 개인화 시연 (합성 사용자, #71)

메인 데모 사용자는 기록이 512개에 못 미쳐 기본 모델에 머문다. 개인화된 "내 기억 패턴" 화면은 합성 사용자로 보여준다.

```bash
uv run khack-optimizer --server http://localhost:8080 --seed-synthetic-user   # 전환 → 합성 기록 → 학습·검증 → 저장·적용
curl -X POST localhost:8080/dev/current-user/reset                            # 메인 데모 사용자로 복귀
```

1. `POST /dev/current-user` — 현재 사용자를 '합성 사용자'로 바꾼다(없으면 만든다).
2. `POST /dev/synthetic-history` — 그 사용자 기록을 합성 기록으로 바꾸고 기본 매개변수로 기억 상태를 만든다. 기본 데모 사용자에게는 거부한다.
3. 평소와 같은 학습·검증을 거쳐, 통과하면 저장하고 적용한다.

합성 사용자 항목에는 실제 대화·세션·문제가 없다. 그 사용자 상태에서는 "내 기억 패턴" 화면만 보여주고 메인 사용자로 돌아온다.
기록만 합성이고 학습·검증·적용은 실제 과정이다. 시연에서 합성 데이터라는 점을 밝힌다.

## 합성 기록

`khack_optimizer/synthetic.py`는 기본값보다 빨리 잊는 가상 사용자를 앱 스케줄(기본 매개변수)대로 복습시킨다. 실제 기억 여부는 그 사용자의 '진짜' 매개변수로 뽑는다.
기본 설정(200항목, 120일, 약 2000개 기록)에서 학습 매개변수가 기본값보다 log loss를 약 7% 낮춘다. 진짜 매개변수가 기본값과 조금만 다르면 학습 결과가 오히려 나빠지기도 하는데, 이때는 저장 기준이 막는다.
