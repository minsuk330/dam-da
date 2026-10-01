import warnings
from datetime import datetime, timezone

from fsrs.optimizer import mini_batch_size
from fsrs.scheduler import DEFAULT_PARAMETERS

from khack_optimizer import cli, synthetic
from khack_optimizer.pipeline import Current, Decision, Settings, run

DEFAULT = Current(version=1, weights=list(DEFAULT_PARAMETERS))


def fail(_train):
    raise AssertionError("기록이 부족하면 옵티마이저를 부르지 않는다")


def test_keeps_parameters_when_reviews_are_below_the_minimum():
    logs = synthetic.generate(cards=10, days=30)

    result = run(logs, DEFAULT, optimize=fail)

    assert result.decision == Decision.INSUFFICIENT_REVIEWS
    assert result.weights is None
    assert result.validation["reviewCount"] == len(logs)


def test_keeps_parameters_when_the_train_split_is_too_small_for_the_optimizer():
    logs = synthetic.generate(cards=40, days=60)

    result = run(logs, DEFAULT, Settings(min_reviews=10), optimize=fail)

    assert result.decision == Decision.INSUFFICIENT_REVIEWS
    assert result.validation["trainScoredReviewCount"] < mini_batch_size


def test_train_split_never_contains_validation_reviews():
    logs = synthetic.generate()
    seen = {}

    def capture(train):
        seen["train"] = train
        return list(DEFAULT_PARAMETERS)

    result = run(logs, DEFAULT, optimize=capture)

    validation_from = datetime.fromisoformat(result.validation["validationFrom"].replace("Z", "+00:00"))
    assert seen["train"] and all(log.review_datetime < validation_from for log in seen["train"])
    assert len(seen["train"]) == result.validation["trainReviewCount"]


def test_does_not_save_when_the_optimized_parameters_are_no_better():
    result = run(synthetic.generate(), DEFAULT, optimize=lambda train: list(DEFAULT_PARAMETERS))

    assert result.decision == Decision.NOT_IMPROVED
    assert result.validation["improvement"] == 0
    assert result.weights is None


def test_must_beat_the_current_parameters_too():
    logs = synthetic.generate()
    truth = synthetic.fast_forgetter()

    result = run(logs, Current(version=2, weights=truth), optimize=lambda train: truth)

    assert result.decision == Decision.NOT_IMPROVED
    assert result.validation["optimized"]["logLoss"] < result.validation["default"]["logLoss"]


def test_learns_and_saves_parameters_for_a_user_who_forgets_faster_than_default():
    with warnings.catch_warnings():
        warnings.simplefilter("ignore", UserWarning)
        result = run(synthetic.generate(), DEFAULT)

    assert result.decision == Decision.SAVE
    assert len(result.weights) == 21
    assert result.validation["improvement"] >= Settings().min_improvement
    assert result.validation["optimized"]["logLoss"] < result.validation["default"]["logLoss"]
    assert result.validation["validationSameDayCount"] > 0


def test_cli_synthetic_run_prints_the_validation_without_saving(capsys):
    assert cli.main(["--synthetic", "--cards", "10", "--days", "30"]) == 0

    out = capsys.readouterr().out
    assert '"reviewCount"' in out
    assert "기존 매개변수 유지" in out
