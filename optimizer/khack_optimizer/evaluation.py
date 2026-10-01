"""예측 성능 평가. 서버 스케줄러(java-fsrs, `FsrsSchedulers`)와 같은 설정으로 기록을 다시 적용한다."""

from __future__ import annotations

import math
from collections.abc import Sequence
from dataclasses import dataclass
from datetime import datetime

from fsrs import Card, Rating, ReviewLog, Scheduler

from .reviews import by_card

BINS = 20
EPSILON = 1e-6


def scheduler(parameters: Sequence[float]) -> Scheduler:
    """서버와 같은 설정: learning·relearning steps 없음, 간격 흔들기 없음."""
    return Scheduler(parameters=parameters, learning_steps=(), relearning_steps=(), enable_fuzzing=False)


@dataclass(frozen=True)
class Score:
    """검증 구간 예측 성능. 낮을수록 좋다."""

    log_loss: float
    rmse_bins: float
    reviews: int

    def to_dict(self) -> dict:
        return {"logLoss": round(self.log_loss, 6), "rmseBins": round(self.rmse_bins, 6), "reviews": self.reviews}


def predictions(logs: list[ReviewLog], parameters: Sequence[float], since: datetime) -> list[tuple[float, int]]:
    """`since` 이후 복습마다 (복습 직전 예측 R, 실제 기억 여부).

    항목의 전체 기록을 처음부터 다시 적용하므로 검증 구간 예측에도 학습 구간 기록이 상태로 들어간다.
    같은 날 재확인(직전 복습과 하루 미만)은 상태에는 반영하되 채점하지 않는다. py-fsrs 옵티마이저 손실과 같은 기준이다.
    """
    fsrs = scheduler(parameters)
    result = []
    for card_id, history in by_card(logs).items():
        card = Card(card_id=card_id, due=history[0].review_datetime)
        for log in history:
            at = log.review_datetime
            if at >= since and card.last_review is not None and (at - card.last_review).days > 0:
                result.append((fsrs.get_card_retrievability(card, at), 0 if log.rating == Rating.Again else 1))
            card, _ = fsrs.review_card(card, log.rating, at)
    return result


def score(points: list[tuple[float, int]]) -> Score:
    if not points:
        return Score(math.nan, math.nan, 0)
    loss = 0.0
    bins: list[list[float]] = [[0, 0.0, 0.0] for _ in range(BINS)]
    for p, y in points:
        p = min(max(p, EPSILON), 1 - EPSILON)
        loss -= y * math.log(p) + (1 - y) * math.log(1 - p)
        b = bins[min(int(p * BINS), BINS - 1)]
        b[0] += 1
        b[1] += p
        b[2] += y
    squared = sum(n * (sp / n - sy / n) ** 2 for n, sp, sy in bins if n)
    return Score(loss / len(points), math.sqrt(squared / len(points)), len(points))


def same_day_reviews(logs: list[ReviewLog], since: datetime) -> int:
    """`since` 이후 같은 날 재확인 건수(채점 제외분)."""
    count = 0
    for history in by_card(logs).values():
        for previous, log in zip(history, history[1:]):
            if log.review_datetime >= since and (log.review_datetime - previous.review_datetime).days == 0:
                count += 1
    return count
