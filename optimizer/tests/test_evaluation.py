import math
from datetime import datetime, timedelta, timezone

import pytest
from fsrs import Rating, ReviewLog
from fsrs.scheduler import DEFAULT_PARAMETERS

from khack_optimizer.evaluation import predictions, same_day_reviews, score
from khack_optimizer.reviews import from_export, to_export

T0 = datetime(2026, 1, 1, 9, tzinfo=timezone.utc)


def log(card, rating, at):
    return ReviewLog(card_id=card, rating=rating, review_datetime=at, review_duration=1000)


def test_same_day_recheck_updates_state_but_is_not_scored():
    logs = [
        log(1, Rating.Again, T0),
        log(1, Rating.Good, T0 + timedelta(minutes=15)),
        log(1, Rating.Good, T0 + timedelta(days=3)),
    ]

    points = predictions(logs, DEFAULT_PARAMETERS, T0)

    assert len(points) == 1
    assert points[0][1] == 1
    assert same_day_reviews(logs, T0) == 1


def test_only_reviews_after_the_split_are_scored_but_history_is_replayed():
    logs = [log(1, Rating.Good, T0), log(1, Rating.Good, T0 + timedelta(days=3)), log(1, Rating.Again, T0 + timedelta(days=12))]

    all_points = predictions(logs, DEFAULT_PARAMETERS, T0)
    late_points = predictions(logs, DEFAULT_PARAMETERS, T0 + timedelta(days=10))

    assert len(all_points) == 2
    assert late_points == all_points[1:]


def test_score_reports_log_loss_and_binned_rmse():
    result = score([(0.9, 1), (0.9, 0)])

    assert result.reviews == 2
    assert result.log_loss == pytest.approx((-math.log(0.9) - math.log(0.1)) / 2)
    assert result.rmse_bins == pytest.approx(0.4)


def test_export_round_trip_keeps_utc_and_sorts_by_time():
    rows = [
        {"card_id": 2, "rating": 3, "review_datetime": "2026-01-02T09:00:00Z", "review_duration": 5},
        {"card_id": 1, "rating": 1, "review_datetime": "2026-01-01T09:00:00Z", "review_duration": 7},
    ]

    logs = from_export(rows)

    assert [l.card_id for l in logs] == [1, 2]
    assert to_export(logs) == list(reversed(rows))
