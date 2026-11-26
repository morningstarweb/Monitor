from __future__ import annotations

from .support import Any, Dict, List, Optional, Tuple, hashlib, json, secrets, time

class CloudflareWorkerSimulator:
    """Accurate white-box simulation of `cloud/src/index.ts` Cloudflare Worker & D1 storage."""

    def __init__(self):
        self.users: Dict[str, Dict[str, Any]] = {}
        self.devices: Dict[str, Dict[str, Any]] = {}
        self.sessions: Dict[str, Dict[str, Any]] = {}
        self.commands: List[Dict[str, Any]] = []
        self.rate_limits: Dict[str, Dict[str, Any]] = {}

    def check_rate_limit(self, client_ip: str, max_requests: int = 80, window_seconds: int = 60) -> bool:
        now = time.time()
        entry = self.rate_limits.get(client_ip)
        if not entry or now > entry["reset_time"]:
            self.rate_limits[client_ip] = {"count": 1, "reset_time": now + window_seconds}
            return True
        if entry["count"] >= max_requests:
            return False
        entry["count"] += 1
        return True

    def register_user_session(self, user_id: str, email: str, device_id: str, role: str = "desktop") -> str:
        token = secrets.token_hex(32)
        hashed_token = hashlib.sha256(token.encode("utf-8")).hexdigest()
        now = int(time.time())

        self.users[user_id] = {"id": user_id, "email": email, "created_at": now}
        self.devices[device_id] = {
            "id": device_id,
            "user_id": user_id,
            "name": f"Device-{device_id}",
            "role": role,
            "is_online": True,
            "last_heartbeat": now,
            "last_snapshot": None,
        }
        self.sessions[hashed_token] = {
            "token_hash": hashed_token,
            "user_id": user_id,
            "device_id": device_id,
            "created_at": now,
            "expires_at": now + 30 * 86400,
        }
        return token

    def authenticate_token(self, raw_token: Optional[str]) -> Optional[Dict[str, Any]]:
        if not raw_token or len(raw_token) != 64:
            return None
        hashed = hashlib.sha256(raw_token.encode("utf-8")).hexdigest()
        session = self.sessions.get(hashed)
        if not session or time.time() > session["expires_at"]:
            return None
        return session

    def handle_heartbeat(
        self,
        token: Optional[str],
        body_bytes: bytes,
        client_ip: str = "127.0.0.1",
    ) -> Tuple[int, Dict[str, Any]]:
        if not self.check_rate_limit(client_ip, 80, 60):
            return 429, {"error": "Too many requests. Please try again later."}

        # 1MB payload ceiling
        if len(body_bytes) > 1024 * 1024:
            return 413, {"error": "Payload too large"}

        session = self.authenticate_token(token)
        if not session:
            return 401, {"error": "Session expired or invalid"}

        try:
            body = json.loads(body_bytes.decode("utf-8"))
        except Exception:
            return 400, {"error": "Invalid JSON body"}

        if not isinstance(body, dict):
            return 400, {"error": "Invalid telemetry payload"}

        dev_id = session["device_id"]
        dev = self.devices.get(dev_id)
        if dev:
            dev["last_heartbeat"] = int(time.time())
            dev["is_online"] = True
            dev["last_snapshot"] = body.get("snapshot")

        # Process reported command results
        results = body.get("command_results", [])
        if isinstance(results, list):
            for res in results:
                cid = res.get("id")
                status = res.get("status", "completed")
                for cmd in self.commands:
                    if cmd["id"] == cid and cmd["user_id"] == session["user_id"]:
                        cmd["status"] = status
                        cmd["result"] = res.get("result")

        # Fetch pending commands for user
        pending = [
            {"id": c["id"], "type": c["type"], "payload": c["payload"]}
            for c in self.commands
            if c["user_id"] == session["user_id"] and c["status"] == "pending"
        ][:5]

        return 200, {
            "success": True,
            "timestamp": int(time.time()),
            "pending_commands": pending,
        }

    def list_user_devices(self, token: Optional[str]) -> Tuple[int, Dict[str, Any]]:
        session = self.authenticate_token(token)
        if not session:
            return 401, {"error": "Session expired or invalid"}

        user_id = session["user_id"]
        # Tenant isolation
        user_devs = [
            {"id": d["id"], "name": d["name"], "role": d["role"], "is_online": d["is_online"]}
            for d in self.devices.values()
            if d["user_id"] == user_id
        ]
        return 200, {"success": True, "devices": user_devs}
