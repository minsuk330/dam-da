"""합성 복습 기록. 기본 매개변수와 다르게 기억하는 가상 사용자를 앱 스케줄대로 복습시킨다.

앱은 기본 매개변수로 다음 복습 시점을 정하고, 실제 기억 여부는 사용자의 '진짜' 매개변수로 계산한 R로 뽑는다.
그래서 이 기록으로 학습한 매개변수는 기본값보다 예측이 나아야 한다. 테스트와 시연용이다.
"""

from __future__ import annotations

import random
from collections.abc import Mapping, Sequence
from datetime import datetime, timedelta, timezone

from fsrs import Card, Rating, ReviewLog
from fsrs.scheduler import DEFAULT_PARAMETERS

from .evaluation import scheduler

START = datetime(2026, 6, 1, 9, 0, tzinfo=timezone.utc)


def fast_forgetter() -> list[float]:
    """기본값보다 빨리 잊는 사용자: 초기 안정도가 낮고 회상 성공 후 안정도 증가가 작다.

    차이가 작으면 옵티마이저가 기본값보다 나빠지기도 해서(그래서 검증이 필요하다), 시연·테스트용으로 차이를 크게 둔다.
    """
    weights = list(DEFAULT_PARAMETERS)
    for i in range(4):
        weights[i] *= 0.3
    weights[8] *= 0.4
    return weights


def generate(
    cards: int = 200,
    days: int = 120,
    seed: int = 7,
    true_weights: Sequence[float] | None = None,
    start: datetime = START,
    intro: Mapping[int, datetime] | None = None,
) -> list[ReviewLog]:
    """`cards`개 항목을 처음 `days`의 절반 동안 고르게 도입하고 `days`일까지 복습한 기록.

    `intro`에 있는 항목은 그 시각에 처음 복습한다(실제 세션의 첫 학습 시각, 로그인 계정 시연 데이터).
    Again 뒤에는 앱 정책(§6.4.8)처럼 같은 날 재확인을 한 번 넣는다.
    """
    rng = random.Random(seed)
    app = scheduler(DEFAULT_PARAMETERS)
    truth = scheduler(true_weights or fast_forgetter())
    end = start + timedelta(days=days)
    logs: list[ReviewLog] = []
    for card_id in range(1, cards + 1):
        at = start + timedelta(days=rng.randrange(days // 2), minutes=rng.randrange(600))
        if intro and card_id in intro:
            at = intro[card_id]
        app_card = Card(card_id=card_id, due=at)
        true_card = Card(card_id=card_id, due=at)
        first = True
        while at < end:
            recalled = not first and rng.random() < truth.get_card_retrievability(true_card, at)
            rating = _rating(rng, recalled) if not first else rng.choice([Rating.Again, Rating.Good, Rating.Good])
            first = False
            app_card, true_card = _review(app, truth, app_card, true_card, rating, at, logs, rng)
            if rating == Rating.Again:
                at += timedelta(minutes=15)
                app_card, true_card = _review(app, truth, app_card, true_card, Rating.Good, at, logs, rng)
            # 사용자가 매일 바로 오지는 않는다: 예정일 당일~이틀 뒤
            at = app_card.due + timedelta(days=rng.choice([0, 0, 1, 2]), minutes=rng.randrange(120))
    return sorted(logs, key=lambda log: (log.review_datetime, log.card_id))


def _rating(rng: random.Random, recalled: bool) -> Rating:
    if not recalled:
        return Rating.Again
    return rng.choices([Rating.Hard, Rating.Good, Rating.Easy], weights=[2, 7, 1])[0]


def _review(app, truth, app_card, true_card, rating, at, logs, rng):
    app_card, _ = app.review_card(app_card, rating, at)
    true_card, _ = truth.review_card(true_card, rating, at)
    logs.append(ReviewLog(card_id=app_card.card_id, rating=rating, review_datetime=at,
                          review_duration=rng.randrange(4_000, 40_000)))
    return app_card, true_card
