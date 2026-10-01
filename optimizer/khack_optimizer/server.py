"""서버 `/dev` API 클라이언트. DB에 직접 접근하지 않는다(AGENTS.md 영속성 규칙)."""

from __future__ import annotations

import json
import os
import urllib.request

from fsrs import ReviewLog

from .pipeline import Current
from .reviews import from_export


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

    def _request(self, method: str, path: str, payload: dict | None = None):
        headers = {"Content-Type": "application/json", "Accept": "application/json"}
        if self.token:
            headers["X-Dev-Token"] = self.token
        data = json.dumps(payload).encode() if payload is not None else None
        request = urllib.request.Request(self.base_url + path, data=data, method=method, headers=headers)
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.load(response)
