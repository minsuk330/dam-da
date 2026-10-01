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

    def _request(self, method: str, path: str, payload: dict | None = None):
        headers = {"Content-Type": "application/json", "Accept": "application/json"}
        if self.token:
            headers["X-Dev-Token"] = self.token
        data = json.dumps(payload).encode() if payload is not None else None
        request = urllib.request.Request(self.base_url + path, data=data, method=method, headers=headers)
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.load(response)
