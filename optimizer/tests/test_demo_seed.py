import json
import warnings
from datetime import datetime, timedelta, timezone

from fsrs import Rating

from khack_optimizer import cli, demo_seed
from khack_optimizer.pipeline import Current
from fsrs.scheduler import DEFAULT_PARAMETERS

NOW = datetime(2026, 10, 2, 0, 0, tzinfo=timezone.utc)


def test_conversation_times_spread_from_oldest_to_a_week_ago():
    times = demo_seed.conversation_times(NOW, 5)

    assert times == sorted(times)
    assert (NOW - times[0]).days in (demo_seed.OLDEST_SESSION_DAYS - 1, demo_seed.OLDEST_SESSION_DAYS)
    assert NOW - times[-1] >= timedelta(days=demo_seed.NEWEST_SESSION_DAYS - 1)


def test_history_starts_real_items_at_their_first_study_and_ends_before_now():
    at = NOW - timedelta(days=30)
    items = [{"memoryItemId": 41, "sessionId": 1, "conversationAt": at.isoformat().replace("+00:00", "Z")},
             {"memoryItemId": 42, "sessionId": 1, "conversationAt": at.isoformat().replace("+00:00", "Z")}]

    links, logs = demo_seed.history(items, NOW, orphans=10)

    assert links == {1: 41, 2: 42}
    first = min(log.review_datetime for log in logs if log.card_id == 1)
    assert first == at + demo_seed.FIRST_STUDY_AFTER
    assert max(log.review_datetime for log in logs) < NOW
    assert {log.card_id for log in logs} == set(range(1, 13))


class FakeServer:
    def __init__(self):
        self.calls = []
        self.logs = []
        self.confirmed = set()

    def switch_user_id(self, user_id):
        self.calls.append(("switch", user_id))
        return {"name": "google:me"}

    def now(self):
        return NOW.isoformat().replace("+00:00", "Z")

    def seed_session(self, session, conversation_at):
        self.calls.append(("seed", session["topicHint"]))
        return {"sessionId": len([c for c in self.calls if c[0] == "seed"])}

    def session_status(self, session_id):
        return "QUESTIONS_READY" if session_id in self.confirmed else "AWAITING_CONFIRMATION"

    def confirm_session(self, session_id):
        self.confirmed.add(session_id)
        return {"goals": ["CORRECT_MISCONCEPTION"], "questions": 2}

    def start_session(self, session_id):
        self.calls.append(("start", session_id))

    def seed_items(self):
        at = (NOW - timedelta(days=20)).isoformat().replace("+00:00", "Z")
        return [{"memoryItemId": 100 + i, "sessionId": 1, "conversationAt": at} for i in range(10)]

    def attach_history(self, links, logs):
        self.calls.append(("attach", len(links)))
        self.logs = logs
        return {"items": len(links), "reviews": len(logs)}

    def review_logs(self):
        return self.logs

    def current(self):
        return Current(version=1, weights=list(DEFAULT_PARAMETERS))

    def register(self, weights, validation):
        self.calls.append(("register", len(weights)))
        return 2

    def activate(self, version):
        self.calls.append(("activate", version))
        return {"recomputed": 10, "skipped": 0}

    def reset_user(self):
        self.calls.append(("reset", None))
        return {"name": "데모 사용자"}


def test_cli_seeds_sessions_and_history_on_an_account_then_trains_activates_and_resets(monkeypatch, tmp_path, capsys):
    for name in ("a", "b"):
        (tmp_path / f"{name}.json").write_text(json.dumps({"topicHint": name, "userTurns": [], "reviewUnits": []}))
    fake = FakeServer()
    monkeypatch.setattr(cli, "Server", lambda url: fake)

    with warnings.catch_warnings():
        warnings.simplefilter("ignore", UserWarning)
        assert cli.main(["--server", "http://x", "--seed-demo-account", "7", "--sessions", str(tmp_path)]) == 0

    assert [call[0] for call in fake.calls] == [
        "switch", "seed", "start", "seed", "start", "attach", "register", "activate", "reset"]
    assert fake.calls[0] == ("switch", 7)
    assert all(log.rating in Rating for log in fake.logs)
    assert "버전 2 적용" in capsys.readouterr().out
