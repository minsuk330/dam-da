"""학습·검증·저장 판정 (스펙 §6.4.9).

시간순으로 앞 구간 기록만 옵티마이저에 주고, 뒤 구간 복습의 예측 성능을 기본·현재·학습 매개변수로 비교한다.
학습 매개변수가 기본값과 현재 값 둘 다보다 정해진 비율 이상 나을 때만 저장한다.
"""

from __future__ import annotations

from collections.abc import Callable, Sequence
from dataclasses import dataclass, field
from enum import Enum

from fsrs import ReviewLog
from fsrs.optimizer import Optimizer, mini_batch_size
from fsrs.scheduler import DEFAULT_PARAMETERS

from . import FSRS_PACKAGE
from .evaluation import Score, predictions, same_day_reviews, score


@dataclass(frozen=True)
class Settings:
    """운영 설정값. CLI 인자로 바꾼다."""

    # py-fsrs 옵티마이저는 학습 구간의 채점 복습(같은 날 재확인·첫 복습 제외)이 mini batch(512개)보다 적으면
    # 학습 없이 기본값을 돌려준다. 첫 복습·재확인·검증 구간을 감안해 전체 등급 기록 1000개를 먼저 요구한다.
    min_reviews: int = 1000
    train_ratio: float = 0.8
    min_improvement: float = 0.02
    min_validation_reviews: int = 30

    def to_dict(self) -> dict:
        return {
            "minReviews": self.min_reviews,
            "trainRatio": self.train_ratio,
            "minImprovement": self.min_improvement,
            "minValidationReviews": self.min_validation_reviews,
        }


@dataclass(frozen=True)
class Current:
    """사용자가 지금 쓰는 매개변수 묶음."""

    version: int
    weights: Sequence[float]


class Decision(Enum):
    SAVE = "save"
    INSUFFICIENT_REVIEWS = "insufficient_reviews"
    NOT_IMPROVED = "not_improved"


@dataclass(frozen=True)
class Result:
    decision: Decision
    reason: str
    validation: dict
    weights: list[float] | None = field(default=None)


def default_optimize(train: list[ReviewLog]) -> list[float]:
    return list(Optimizer(train).compute_optimal_parameters())


def run(
    logs: list[ReviewLog],
    current: Current,
    settings: Settings = Settings(),
    optimize: Callable[[list[ReviewLog]], list[float]] = default_optimize,
) -> Result:
    logs = sorted(logs, key=lambda log: log.review_datetime)
    validation = {"fsrsPackage": FSRS_PACKAGE, "settings": settings.to_dict(), "reviewCount": len(logs)}
    if len(logs) < settings.min_reviews:
        return Result(
            Decision.INSUFFICIENT_REVIEWS,
            f"등급 기록 {len(logs)}개 < 최소 {settings.min_reviews}개. 기존 매개변수 유지",
            validation,
        )

    cut = logs[int(len(logs) * settings.train_ratio)].review_datetime
    train = [log for log in logs if log.review_datetime < cut]
    validation.update(
        trainReviewCount=len(train),
        trainFrom=_iso(logs[0].review_datetime),
        validationFrom=_iso(cut),
        validationTo=_iso(logs[-1].review_datetime),
        validationSameDayCount=same_day_reviews(logs, cut),
    )

    train_scored = len(predictions(train, DEFAULT_PARAMETERS, logs[0].review_datetime))
    validation["trainScoredReviewCount"] = train_scored
    if train_scored < mini_batch_size:
        return Result(
            Decision.INSUFFICIENT_REVIEWS,
            f"학습 구간 채점 복습 {train_scored}개 < 옵티마이저 최소 {mini_batch_size}개. 기존 매개변수 유지",
            validation,
        )

    scores = {"default": score(predictions(logs, DEFAULT_PARAMETERS, cut))}
    scores["current"] = score(predictions(logs, current.weights, cut))
    validation["validationReviewCount"] = scores["default"].reviews
    if scores["default"].reviews < settings.min_validation_reviews:
        validation.update(default=scores["default"].to_dict(), current=_current(current, scores["current"]))
        return Result(
            Decision.INSUFFICIENT_REVIEWS,
            f"검증 구간 채점 복습 {scores['default'].reviews}개 < 최소 {settings.min_validation_reviews}개. 기존 매개변수 유지",
            validation,
        )

    weights = [float(w) for w in optimize(train)]
    scores["optimized"] = score(predictions(logs, weights, cut))
    baseline = min(scores["default"].log_loss, scores["current"].log_loss)
    improvement = (baseline - scores["optimized"].log_loss) / baseline
    validation.update(
        default=scores["default"].to_dict(),
        current=_current(current, scores["current"]),
        optimized=scores["optimized"].to_dict(),
        improvement=round(improvement, 6),
    )
    if improvement < settings.min_improvement:
        return Result(
            Decision.NOT_IMPROVED,
            f"log loss 개선 {improvement:.1%} < 기준 {settings.min_improvement:.1%}. 기존 매개변수 유지",
            validation,
        )
    return Result(Decision.SAVE, f"log loss 개선 {improvement:.1%}. 새 버전으로 저장", validation, weights)


def _current(current: Current, score: Score) -> dict:
    return {"version": current.version, **score.to_dict()}


def _iso(value) -> str:
    return value.isoformat().replace("+00:00", "Z")
