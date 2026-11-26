"""Deterministic in-memory protocol harness for Monitor E2E testing.

Simulates:
- Desktop Agent Axum endpoints (Port 9480)
- Cloudflare Edge Broker Worker & D1 database
- WebSocket live streaming & ticket validation
- Network latency, rate limiting, and failover injection
"""

from __future__ import annotations

import json
import secrets
import time
from typing import Any, Callable, Dict, List, Optional, Tuple

from tests.e2e.helpers.contract_models import (
    BatterySnapshot,
    CpuSnapshot,
    DiskSnapshot,
    GpuSnapshot,
    MemorySnapshot,
    PullRequestInfo,
    TelemetrySnapshot,
)


class MockDesktopAgent:
    """Emulates the Rust Desktop Agent HTTP and WebSocket server (Port 9480)."""

    def __init__(self, port: int = 9480):
        self.port = port
        self.active_pairing_otp = "749201"
        self.issued_tokens: Dict[str, Dict[str, Any]] = {}
        self.ws_tickets: Dict[str, float] = {}  # ticket -> expiry timestamp
        self.projects: List[Dict[str, Any]] = [
            {
                "repoId": "monitor-core",
                "displayName": "Monitor Core",
                "path": "D:\\Monitor",
                "activeBranch": "main",
                "isDirty": False,
                "uncommittedCount": 0,
            }
        ]
        self.pull_requests: Dict[str, List[PullRequestInfo]] = {
            "monitor-core": [
                PullRequestInfo(
                    number=42,
                    title="feat: hardware telemetry hardening",
                    author="lead-dev",
                    state="open",
                    base_branch="main",
                    head_branch="feature/hardening",
                    updated_at="2026-11-08T12:00:00Z",
                    url="https://github.com/monitor-app/monitor/pull/42",
                )
            ]
        }
        self.killed_processes: List[int] = []
        self.cooling_sensor_active = False

    def handle_pair(self, body: Dict[str, Any]) -> Tuple[int, Dict[str, Any]]:
        otp = body.get("otp", "")
        if otp != self.active_pairing_otp:
            return 401, {"error": "Invalid pairing code"}

        token = secrets.token_hex(32)
        expires_at = int(time.time()) + 30 * 86400
        self.issued_tokens[token] = {
            "device_name": body.get("deviceName", "Unknown"),
            "client_uuid": body.get("clientUuid", ""),
            "expires_at": expires_at,
        }
        return 200, {
            "token": token,
            "expiresAt": expires_at,
            "hostName": "DEVELOPER-PC",
        }

    def handle_ws_ticket(self, auth_header: Optional[str]) -> Tuple[int, Dict[str, Any]]:
        if not self._check_auth(auth_header):
            return 401, {"error": "Unauthorized"}

        ticket = secrets.token_hex(16)
        self.ws_tickets[ticket] = time.time() + 30  # 30-sec TTL
        return 200, {"ticket": ticket, "ttlSeconds": 30}

    def handle_get_snapshot(self, auth_header: Optional[str]) -> Tuple[int, Dict[str, Any]]:
        if not self._check_auth(auth_header):
            return 401, {"error": "Unauthorized"}

        snapshot = TelemetrySnapshot(
            observed_at_utc_ms=int(time.time() * 1000),
            cpu=CpuSnapshot(total_usage_percent=24.5, core_count=16),
            memory=MemorySnapshot(used_bytes=16_000_000_000, total_bytes=34_359_738_368),
            gpu=GpuSnapshot(usage_percent=12.0, temperature_celsius=46.0),
            battery=BatterySnapshot(level_percent=92, is_charging=True, remaining_seconds=18000),
            disks=[
                DiskSnapshot(
                    name="C:",
                    mount_point="C:\\",
                    total_bytes=1_000_000_000_000,
                    available_bytes=550_000_000_000,
                )
            ],
            cooling={"rpm": 1200} if self.cooling_sensor_active else None,
        )
        return 200, snapshot.to_dict()

    def handle_kill_process(self, auth_header: Optional[str], pid: int) -> Tuple[int, Dict[str, Any]]:
        if not self._check_auth(auth_header):
            return 401, {"error": "Unauthorized"}

        if pid <= 4:
            return 403, {"error": "Permission denied: System process protected"}

        self.killed_processes.append(pid)
        return 200, {"success": True, "pid": pid}

    def handle_get_pulls(self, auth_header: Optional[str], repo_id: str) -> Tuple[int, Any]:
        if not self._check_auth(auth_header):
            return 401, {"error": "Unauthorized"}

        prs = self.pull_requests.get(repo_id, [])
        return 200, [pr.to_dict() for pr in prs]

    def handle_merge_pull(
        self, auth_header: Optional[str], repo_id: str, pr_number: int, body: Dict[str, Any]
    ) -> Tuple[int, Dict[str, Any]]:
        if not self._check_auth(auth_header):
            return 401, {"error": "Unauthorized"}

        method = body.get("method", "merge")
        if method not in ("merge", "squash", "rebase"):
            return 400, {"error": f"Unsupported merge method: {method}"}

        prs = self.pull_requests.get(repo_id, [])
        matching = [pr for pr in prs if pr.number == pr_number]
        if not matching:
            return 404, {"error": f"Pull request #{pr_number} not found"}

        matching[0].state = "merged"
        return 200, {"success": True, "message": f"PR #{pr_number} successfully merged via {method}"}

    def _check_auth(self, auth_header: Optional[str]) -> bool:
        if not auth_header or not auth_header.startswith("Bearer "):
            return False
        token = auth_header[7:].strip()
        data = self.issued_tokens.get(token)
        if not data:
            return False
        return time.time() < data["expires_at"]


class MockCloudflareBroker:
    """Emulates Cloudflare Edge Broker Worker with D1 database storage."""

    def __init__(self):
        self.devices: Dict[str, Dict[str, Any]] = {}
        self.sessions: Dict[str, Dict[str, Any]] = {}
        self.commands: List[Dict[str, Any]] = []
        self.rate_limits: Dict[str, List[float]] = {}
        self.latency_ms = 0

    def handle_google_verify(self, body: Dict[str, Any], client_ip: str = "127.0.0.1") -> Tuple[int, Dict[str, Any]]:
        id_token = body.get("id_token")
        device_id = body.get("device_id")
        device_name = body.get("device_name")
        if not id_token or not device_id or not device_name:
            return 400, {"error": "Missing required authentication fields"}

        # Simulate Google verification
        user_id = body.get("user_id") or ("google_sub_12345" if id_token in ("valid_google_oidc_token", "google_token_pair") else f"sub_{id_token}")
        email = "developer@example.com"
        session_token = secrets.token_hex(32)
        now = int(time.time())

        self.devices[device_id] = {
            "id": device_id,
            "user_id": user_id,
            "name": device_name,
            "role": body.get("role", "desktop"),
            "lan_ip": body.get("lan_ip", "192.168.1.100"),
            "lan_port": body.get("lan_port", 9480),
            "is_online": True,
            "last_heartbeat": now,
            "last_snapshot": None,
            "last_projects": [],
            "last_workspaces": {},
            "last_pull_requests": {},
        }
        self.sessions[session_token] = {
            "user_id": user_id,
            "device_id": device_id,
            "expires_at": now + 30 * 86400,
        }
        return 200, {
            "success": True,
            "token": session_token,
            "user": {"id": user_id, "email": email, "name": "Developer"},
            "device": {"id": device_id, "name": device_name},
        }

    def handle_heartbeat(self, auth_header: Optional[str], body: Dict[str, Any]) -> Tuple[int, Dict[str, Any]]:
        session = self._verify_session(auth_header)
        if not session:
            return 401, {"error": "Unauthorized"}

        device_id = session["device_id"]
        dev = self.devices.get(device_id)
        if dev:
            dev["last_heartbeat"] = int(time.time())
            if "snapshot" in body:
                dev["last_snapshot"] = body["snapshot"]
            if "projects" in body:
                dev["last_projects"] = body["projects"]
            if "workspaces" in body:
                dev["last_workspaces"] = body["workspaces"]
            if "pull_requests" in body:
                dev["last_pull_requests"] = body["pull_requests"]

        # Fetch pending commands
        pending = [c for c in self.commands if c["status"] == "pending" and c["user_id"] == session["user_id"]]
        for c in pending:
            c["status"] = "dispatched"

        return 200, {
            "success": True,
            "pending_commands": pending,
        }

    def handle_get_telemetry(self, auth_header: Optional[str]) -> Tuple[int, Dict[str, Any]]:
        session = self._verify_session(auth_header)
        if not session:
            return 401, {"error": "Unauthorized"}

        user_id = session["user_id"]
        # Find desktop device
        desktops = [d for d in self.devices.values() if d["user_id"] == user_id and d["role"] == "desktop"]
        if not desktops:
            return 404, {"error": "No desktop registered"}

        desk = desktops[0]
        return 200, {
            "success": True,
            "desktop": {
                "id": desk["id"],
                "name": desk["name"],
                "lan_ip": desk["lan_ip"],
                "lan_port": desk["lan_port"],
                "is_online": desk["is_online"],
            },
            "snapshot": desk["last_snapshot"],
            "projects": desk["last_projects"],
            "workspaces": desk["last_workspaces"],
            "pull_requests": desk["last_pull_requests"],
        }

    def handle_cloud_kill_process(self, auth_header: Optional[str], pid: int) -> Tuple[int, Dict[str, Any]]:
        session = self._verify_session(auth_header)
        if not session:
            return 401, {"error": "Unauthorized"}
        if pid <= 0 or pid == 4:
            return 400, {"error": "Protected process cannot be terminated"}
        user_id = session["user_id"]
        desktops = [d for d in self.devices.values() if d["user_id"] == user_id and d["role"] == "desktop"]
        if not desktops:
            return 404, {"error": "No desktop registered"}
        cmd_id = self.queue_command(user_id, desktops[0]["id"], "kill_process", {"pid": pid})
        return 200, {"success": True, "command_id": cmd_id}

    def queue_command(self, user_id: str, device_id: str, cmd_type: str, payload: Dict[str, Any]) -> str:
        cmd_id = f"cmd_{secrets.token_hex(8)}"
        self.commands.append({
            "id": cmd_id,
            "user_id": user_id,
            "device_id": device_id,
            "type": cmd_type,
            "payload": payload,
            "status": "pending",
        })
        return cmd_id

    def _verify_session(self, auth_header: Optional[str]) -> Optional[Dict[str, Any]]:
        if not auth_header or not auth_header.startswith("Bearer "):
            return None
        token = auth_header[7:].strip()
        session = self.sessions.get(token)
        if not session or time.time() > session["expires_at"]:
            return None
        return session
