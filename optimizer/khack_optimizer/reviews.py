"""서버 내보내기(`GET /dev/review-logs.json`)와 py-fsrs `ReviewLog` 사이 변환."""

from __future__ import annotations

from datetime import datetime

from fsrs import Rating, ReviewLog


def from_export(rows: list[dict]) -> list[ReviewLog]:
    """서버가 내보낸 등급 기록(보류 제외)을 시간순 `ReviewLog`로 바꾼다."""
    logs = [
        ReviewLog(
            card_id=int(row["card_id"]),
            rating=Rating(int(row["rating"])),
            review_datetime=_parse(row["review_datetime"]),
            review_duration=row.get("review_duration"),
        )
        for row in rows
    ]
    return sorted(logs, key=lambda log: (log.review_datetime, log.card_id))


def to_export(logs: list[ReviewLog]) -> list[dict]:
    return [
        {
            "card_id": log.card_id,
            "rating": int(log.rating),
            "review_datetime": log.review_datetime.isoformat().replace("+00:00", "Z"),
            "review_duration": log.review_duration,
        }
        for log in logs
    ]


def by_card(logs: list[ReviewLog]) -> dict[int, list[ReviewLog]]:
    cards: dict[int, list[ReviewLog]] = {}
    for log in sorted(logs, key=lambda log: log.review_datetime):
        cards.setdefault(log.card_id, []).append(log)
    return cards


def _parse(value: str) -> datetime:
    return datetime.fromisoformat(value.replace("Z", "+00:00"))
