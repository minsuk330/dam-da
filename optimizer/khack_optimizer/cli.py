"""개인 매개변수 학습 배치 실행.

    uv run khack-optimizer --server http://localhost:8080           # 현재 사용자 기록으로 학습·검증, 개선되면 저장
    uv run khack-optimizer --server http://localhost:8080 --dry-run # 저장하지 않고 결과만
    uv run khack-optimizer --synthetic                              # 서버 없이 합성 기록으로 확인
"""

from __future__ import annotations

import argparse
import json
import warnings

from fsrs.scheduler import DEFAULT_PARAMETERS

from . import synthetic
from .pipeline import Current, Decision, Settings, run
from .server import Server


def main(argv: list[str] | None = None) -> int:
    defaults = Settings()
    parser = argparse.ArgumentParser(prog="khack-optimizer", description="개인 FSRS 매개변수 학습·검증 (스펙 §6.4.9)")
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--server", help="백엔드 주소. DEV_TOOLS_ENABLED=true로 떠 있어야 한다")
    source.add_argument("--synthetic", action="store_true", help="합성 기록으로 실행(저장 안 함)")
    parser.add_argument("--dry-run", action="store_true", help="개선돼도 저장하지 않는다")
    parser.add_argument("--min-reviews", type=int, default=defaults.min_reviews)
    parser.add_argument("--train-ratio", type=float, default=defaults.train_ratio)
    parser.add_argument("--min-improvement", type=float, default=defaults.min_improvement, help="log loss 상대 개선 기준")
    parser.add_argument("--min-validation-reviews", type=int, default=defaults.min_validation_reviews)
    parser.add_argument("--cards", type=int, default=200, help="--synthetic 항목 수")
    parser.add_argument("--days", type=int, default=120, help="--synthetic 기간(일)")
    parser.add_argument("--seed", type=int, default=7, help="--synthetic 난수 시드")
    args = parser.parse_args(argv)

    settings = Settings(args.min_reviews, args.train_ratio, args.min_improvement, args.min_validation_reviews)
    if args.synthetic:
        server = None
        logs = synthetic.generate(cards=args.cards, days=args.days, seed=args.seed)
        current = Current(version=1, weights=list(DEFAULT_PARAMETERS))
    else:
        server = Server(args.server)
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
            print(f"매개변수 버전 {server.register(result.weights, result.validation)} 저장. 적용은 별도로 한다(#25)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
