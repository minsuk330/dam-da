"""개인 매개변수 학습 배치 실행.

    uv run khack-optimizer --server http://localhost:8080           # 현재 사용자 기록으로 학습·검증, 개선되면 저장
    uv run khack-optimizer --server http://localhost:8080 --dry-run # 저장하지 않고 결과만
    uv run khack-optimizer --synthetic                              # 서버 없이 합성 기록으로 확인
    uv run khack-optimizer --server http://localhost:8080 --seed-synthetic-user  # 개인화 시연용 합성 사용자 준비
    uv run khack-optimizer --server http://localhost:8080 --seed-demo-account 7 --sessions ../backend/fixtures/demo-seed
                                                                    # 로그인 계정에 시연 세션 + 합성 기록 → 학습·저장·적용
"""

from __future__ import annotations

import argparse
import json
import warnings
from datetime import datetime
from pathlib import Path

from fsrs.scheduler import DEFAULT_PARAMETERS

from . import demo_seed, synthetic
from .pipeline import Current, Decision, Settings, run
from .server import Server

SYNTHETIC_USER = "합성 사용자"


def main(argv: list[str] | None = None) -> int:
    defaults = Settings()
    parser = argparse.ArgumentParser(prog="khack-optimizer", description="개인 FSRS 매개변수 학습·검증 (스펙 §6.4.9)")
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--server", help="백엔드 주소. DEV_TOOLS_ENABLED=true로 떠 있어야 한다")
    source.add_argument("--synthetic", action="store_true", help="합성 기록으로 실행(저장 안 함)")
    parser.add_argument("--dry-run", action="store_true", help="개선돼도 저장하지 않는다")
    parser.add_argument("--activate", action="store_true", help="저장한 버전을 바로 현재 사용자에게 적용한다")
    parser.add_argument(
        "--seed-synthetic-user",
        nargs="?",
        const=SYNTHETIC_USER,
        metavar="NAME",
        help=f"(--server) 합성 사용자(기본 '{SYNTHETIC_USER}')로 전환하고 기록을 합성 기록으로 바꾼 뒤 학습·저장·적용한다. "
        "현재 사용자는 그 사용자로 남는다. 복귀는 POST /dev/current-user/reset",
    )
    parser.add_argument(
        "--seed-demo-account",
        type=int,
        metavar="USER_ID",
        help="(--server) 그 로그인 계정으로 전환하고 --sessions의 세션을 과거 시각으로 저장한 뒤, 합성 복습 기록을 붙여 "
        "학습·저장·적용한다. 끝나면 기본 데모 사용자로 돌아간다",
    )
    parser.add_argument("--sessions", type=Path, help="--seed-demo-account 세션 JSON 폴더. 없으면 세션 저장을 건너뛴다")
    parser.add_argument("--orphans", type=int, default=150, help="--seed-demo-account 기록만 있는 과거 항목 수")
    parser.add_argument("--min-reviews", type=int, default=defaults.min_reviews)
    parser.add_argument("--train-ratio", type=float, default=defaults.train_ratio)
    parser.add_argument("--min-improvement", type=float, default=defaults.min_improvement, help="log loss 상대 개선 기준")
    parser.add_argument("--min-validation-reviews", type=int, default=defaults.min_validation_reviews)
    parser.add_argument("--epochs", type=int, default=defaults.epochs, help="옵티마이저 epoch 수")
    parser.add_argument("--cards", type=int, default=200, help="--synthetic 항목 수")
    parser.add_argument("--days", type=int, default=120, help="--synthetic 기간(일)")
    parser.add_argument("--seed", type=int, default=7, help="--synthetic 난수 시드")
    args = parser.parse_args(argv)

    if (args.seed_synthetic_user or args.seed_demo_account) and not args.server:
        parser.error("--seed-synthetic-user, --seed-demo-account에는 --server가 필요하다")

    settings = Settings(args.min_reviews, args.train_ratio, args.min_improvement, args.min_validation_reviews, args.epochs)
    if args.synthetic:
        server = None
        logs = synthetic.generate(cards=args.cards, days=args.days, seed=args.seed)
        current = Current(version=1, weights=list(DEFAULT_PARAMETERS))
    else:
        server = Server(args.server)
        if args.seed_synthetic_user:
            print(f"현재 사용자 → {server.switch_user(args.seed_synthetic_user)['name']}")
            imported = server.replace_history(synthetic.generate(cards=args.cards, days=args.days, seed=args.seed))
            print(f"합성 기록 {imported['reviews']}개(항목 {imported['items']}개) 저장")
        if args.seed_demo_account:
            seed_demo_account(server, args.seed_demo_account, args.sessions, args.orphans, args.seed)
        logs = server.review_logs()
        current = server.current()

    with warnings.catch_warnings():
        warnings.simplefilter("ignore", UserWarning)  # py-fsrs 내부 torch 경고
        result = run(logs, current, settings)

    print(json.dumps(result.validation, ensure_ascii=False, indent=2))
    print(result.reason)
    if result.decision == Decision.SAVE:
        if server is None or args.dry_run:
            print("저장 생략(--synthetic 또는 --dry-run)")
        else:
            version = server.register(result.weights, result.validation)
            print(f"매개변수 버전 {version} 저장")
            if args.activate or args.seed_synthetic_user or args.seed_demo_account:
                activated = server.activate(version)
                print(f"버전 {version} 적용: 기억 상태 재계산 {activated['recomputed']}개, 건너뜀 {activated['skipped']}개")
    if args.seed_demo_account:
        print(f"현재 사용자 → {server.reset_user()['name']}")
    return 0


def seed_demo_account(server: Server, user_id: int, sessions: Path | None, orphans: int, seed: int) -> None:
    print(f"현재 사용자 → {server.switch_user_id(user_id)['name']}")
    now = datetime.fromisoformat(server.now().replace("Z", "+00:00"))
    if sessions:
        files = sorted(sessions.glob("*.json"))
        demo_seed.seed_sessions(server, files, now)
        print(f"세션 {len(files)}개 저장")
    items = server.seed_items()
    links, logs = demo_seed.history(items, now, orphans, seed)
    attached = server.attach_history(links, logs)
    print(f"합성 기록 {attached['reviews']}개를 실제 항목 {attached['items']}개와 과거 항목 {orphans}개에 붙임")


if __name__ == "__main__":
    raise SystemExit(main())
