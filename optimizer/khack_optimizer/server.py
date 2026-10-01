"""서버 `/dev` API 클라이언트. DB에 직접 접근하지 않는다(AGENTS.md 영속성 규칙)."""

from __future__ import annotations

import json
import os
import urllib.request

from fsrs import ReviewLog

from .pipeline import Current
from .reviews import from_export, to_export


class Server:
    """`/dev/**`는 로컬 요청이거나 `X-Dev-Token`이 `DEV_TOOLS_TOKEN`과 같아야 열린다."""

    def __init__(self, base_url: str, token: str | None = None):
        self.base_url = base_url.rstrip("/")
        self.token = token if token is not None else os.environ.get("DEV_TOOLS_TOKEN")

    def review_logs(self) -> list[ReviewLog]:
        return from_export(self._request("GET", "/dev/review-logs.json"))

    def current(self) -> Current:
        body = self._request("GET", "/dev/fsrs-parameters/active")
        return Current(version=body["version"], weights=body["weights"])

    def register(self, weights: list[float], validation: dict) -> int:
        body = self._request("POST", "/dev/fsrs-parameters", {"weights": weights, "validation": validation})
        return body["version"]

    def activate(self, version: int) -> dict:
        """현재 사용자에게 버전을 적용하고 기억 상태를 다시 계산한다."""
        return self._request("POST", f"/dev/fsrs-parameters/{version}/activate")

    def switch_user(self, name: str) -> dict:
        return self._request("POST", "/dev/current-user", {"name": name})

    def reset_user(self) -> dict:
        return self._request("POST", "/dev/current-user/reset")

    def replace_history(self, logs: list[ReviewLog]) -> dict:
        """현재 사용자 기록을 합성 기록으로 바꾼다. 기본 데모 사용자면 서버가 거부한다."""
        return self._request("POST", "/dev/synthetic-history", to_export(logs))

    def switch_user_id(self, user_id: int) -> dict:
        """이미 있는 사용자(로그인 계정)로 개발 도구 사용자를 바꾼다."""
        return self._request("POST", "/dev/current-user", {"id": user_id})

    def now(self) -> str:
        """서버 시계(시간 이동 반영)의 지금 시각."""
        return self._request("GET", "/dev/clock")["now"]

    def seed_session(self, session: dict, conversation_at: str) -> dict:
        return self._request("POST", "/dev/demo-seed/sessions", {"session": session, "conversationAt": conversation_at})

    def session_status(self, session_id: int) -> str:
        return self._request("GET", f"/dev/demo-seed/sessions/{session_id}")["status"]

    def confirm_session(self, session_id: int) -> dict:
        return self._request("POST", f"/dev/demo-seed/sessions/{session_id}/confirm", {})

    def start_session(self, session_id: int) -> dict:
        return self._request("POST", f"/dev/demo-seed/sessions/{session_id}/start")

    def seed_items(self) -> list[dict]:
        return self._request("GET", "/dev/demo-seed/items")

    def attach_history(self, links: dict[int, int], logs: list[ReviewLog]) -> dict:
        """합성 기록을 현재 사용자의 실제 기억 항목(card_id → memory_item_id)에 붙인다. 다른 데이터는 지우지 않는다."""
        body = {
            "links": [{"card_id": card, "memory_item_id": item} for card, item in links.items()],
            "reviews": to_export(logs),
        }
        return self._request("POST", "/dev/synthetic-history/attach", body)

    def _request(self, method: str, path: str, payload: dict | None = None):
        headers = {"Content-Type": "application/json", "Accept": "application/json"}
        if self.token:
            headers["X-Dev-Token"] = self.token
        data = json.dumps(payload).encode() if payload is not None else None
        request = urllib.request.Request(self.base_url + path, data=data, method=method, headers=headers)
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.load(response)
