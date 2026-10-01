"""py-fsrs가 서버 java-fsrs와 같은 FSRS 계산을 하는지 확인한다. 같은 기대값을 backend FsrsGoldenTest도 확인한다."""

import json
from datetime import datetime
from pathlib import Path

import pytest
from fsrs import Card, Rating
from fsrs.scheduler import DEFAULT_PARAMETERS

from khack_optimizer.evaluation import scheduler

GOLDEN = json.loads((Path(__file__).parent / "fixtures" / "java_fsrs_golden.json").read_text())


def test_default_parameters_match_java_fsrs():
    assert list(DEFAULT_PARAMETERS) == GOLDEN["cases"][0]["parameters"]
    assert len(DEFAULT_PARAMETERS) == 21


@pytest.mark.parametrize("case", GOLDEN["cases"], ids=lambda case: case["name"])
def test_replay_matches_java_fsrs(case):
    fsrs = scheduler(case["parameters"])
    card = None
    for review in case["reviews"]:
        at = datetime.fromisoformat(review["datetime"].replace("Z", "+00:00"))
        card = card or Card(card_id=1, due=at)
        if review["retrievability_before"] is not None:
            assert fsrs.get_card_retrievability(card, at) == pytest.approx(review["retrievability_before"], abs=1e-9)
        card, _ = fsrs.review_card(card, Rating(review["rating"]), at)
        assert card.stability == pytest.approx(review["stability"], abs=1e-9)
        assert card.difficulty == pytest.approx(review["difficulty"], abs=1e-9)
        assert card.due == datetime.fromisoformat(review["due"].replace("Z", "+00:00"))
