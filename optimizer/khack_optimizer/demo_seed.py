"""로그인 계정에 시연용 학습 기록을 넣는다 (발표 시연: 여러 세션 + 개인화된 기억 모델).

1. 개발 도구 사용자를 그 계정으로 바꾼다.
2. 세션 JSON(커넥터 입력 형식)을 과거 대화 시각으로 저장하고, 앱과 같은 흐름(검수 → 확인 → 학습 목표 → 문제 생성)을 거쳐
   첫 학습을 시작한 상태로 둔다.
3. 합성 복습 기록을 실제 기억 항목에 붙인다. 512개 기준을 채우는 나머지는 기록만 있는 과거 항목이다.
기록만 합성이고, 이후 학습·검증·적용은 평소 과정이다(cli가 이어서 한다). 시연에서 합성 기록이라는 점을 밝힌다.
"""

from __future__ import annotations

import json
import time
from collections.abc import Callable
from datetime import datetime, timedelta
from pathlib import Path

from fsrs import ReviewLog

from . import synthetic
from .server import Server

# 세션 대화 시각을 지금으로부터 이 범위(일 전)에 고르게 둔다. 가장 최근 세션도 일주일 전이다.
OLDEST_SESSION_DAYS = 110
NEWEST_SESSION_DAYS = 8
# 기록만 있는 과거 항목이 시작하는 시점(일 전). 이 기간의 앞 절반에 고르게 도입한다.
HISTORY_DAYS = 150
# 대화 뒤 첫 학습까지 걸린 시간
FIRST_STUDY_AFTER = timedelta(hours=2)


def conversation_times(now: datetime, count: int) -> list[datetime]:
    """세션 `count`개의 대화 시각. 오래된 것부터 고르게, 시각은 저녁 무렵으로 흩는다."""
    if count == 1:
        return [now - timedelta(days=NEWEST_SESSION_DAYS)]
    step = (OLDEST_SESSION_DAYS - NEWEST_SESSION_DAYS) / (count - 1)
    return [
        (now - timedelta(days=OLDEST_SESSION_DAYS - round(i * step))).replace(hour=11 + i % 3, minute=7 * i % 60, second=0,
                                                                            microsecond=0)
        for i in range(count)
    ]


def seed_sessions(server: Server, files: list[Path], now: datetime, log: Callable[[str], None] = print,
                  timeout: float = 600, poll: float = 3) -> list[int]:
    """세션을 하나씩 저장하고 첫 학습 시작까지 진행한다. 검수·문제 생성은 비동기라 상태를 보며 넘긴다."""
    seeded = []
    for path, at in zip(files, conversation_times(now, len(files)), strict=True):
        session = json.loads(path.read_text(encoding="utf-8"))
        session_id = server.seed_session(session, _iso(at))["sessionId"]
        log(f"{path.stem}: 세션 {session_id} 저장 ({at:%Y-%m-%d})")
        _await(server, session_id, "AWAITING_CONFIRMATION", timeout, poll)
        confirmed = server.confirm_session(session_id)
        log(f"  확인·목표 {', '.join(confirmed['goals'])}, 문제 {confirmed['questions']}개 생성 중")
        _await(server, session_id, "QUESTIONS_READY", timeout, poll)
        server.start_session(session_id)
        seeded.append(session_id)
    return seeded


def history(items: list[dict], now: datetime, orphans: int, seed: int = 7) -> tuple[dict[int, int], list[ReviewLog]]:
    """실제 항목(card 1..n)은 첫 학습 시각부터, 과거 항목은 그 뒤 번호로 `HISTORY_DAYS` 전부터 지금까지 복습한 합성 기록."""
    links = {card: item["memoryItemId"] for card, item in enumerate(items, start=1)}
    intro = {card: _parse(item["conversationAt"]) + FIRST_STUDY_AFTER for card, item in enumerate(items, start=1)}
    logs = synthetic.generate(cards=len(items) + orphans, days=HISTORY_DAYS, seed=seed,
                              start=now - timedelta(days=HISTORY_DAYS), intro=intro)
    return links, [log for log in logs if log.review_datetime < now]


def _await(server: Server, session_id: int, status: str, timeout: float, poll: float) -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if server.session_status(session_id) == status:
            return
        time.sleep(poll)
    raise TimeoutError(f"세션 {session_id}가 {timeout:.0f}초 안에 {status}가 되지 않았다")


def _parse(value: str) -> datetime:
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


def _iso(value: datetime) -> str:
    return value.isoformat().replace("+00:00", "Z")
