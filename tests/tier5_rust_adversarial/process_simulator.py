from __future__ import annotations

from .support import Any, Dict, Optional, Tuple, os

class RustWhiteBoxProcessSimulator:
    """Accurate white-box simulation of `monitor-telemetry::system` and `monitor-transport::handlers`."""

    def __init__(self, current_pid: Optional[int] = None):
        self.current_pid = current_pid or os.getpid()
        self.allow_delete_operations: bool = False
        self.allow_git_operations: bool = True
        self.active_processes: Dict[int, str] = {
            0: "System Idle Process",
            4: "System",
            self.current_pid: "monitor.exe",
            1000: "explorer.exe",
            1024: "cmd.exe",
            2048: "powershell.exe",
        }

    def parse_path_pid(self, raw_pid: Any) -> Tuple[Optional[int], Optional[Tuple[int, str]]]:
        """Simulates Axum Path<u32> extractor."""
        if isinstance(raw_pid, int):
            if raw_pid < 0 or raw_pid > 4294967295:
                return None, (400, "Path extraction failed: invalid u32 integer")
            return raw_pid, None
        if isinstance(raw_pid, str):
            if not raw_pid.isdigit():
                return None, (400, "Path extraction failed: invalid u32 integer")
            val = int(raw_pid)
            if val < 0 or val > 4294967295:
                return None, (400, "Path extraction failed: u32 integer overflow")
            return val, None
        return None, (400, "Path extraction failed: unsupported type")

    def handle_http_kill_process(
        self,
        token_role: Optional[str],
        raw_pid: Any,
    ) -> Tuple[int, Dict[str, Any]]:
        """Simulates `POST /api/v1/system/processes/{pid}/kill`."""
        if token_role is None:
            return 401, {"error": "Unauthorized", "message": "Missing Bearer token"}

        # 1. RBAC check: Viewer is strictly forbidden BEFORE anything else
        if token_role.lower() == "viewer":
            return 403, {
                "error": "Forbidden",
                "message": "Insufficient permissions: Viewer role cannot terminate system processes.",
            }

        # 2. Path parameter extraction check
        pid, err = self.parse_path_pid(raw_pid)
        if err is not None:
            return err[0], {"error": "BadRequest", "message": err[1]}

        assert pid is not None

        # 3. Tray delete policy check
        if not self.allow_delete_operations:
            return 403, {
                "error": "Forbidden",
                "message": "Process termination is blocked by desktop tray policy. Please enable Delete Access in the Windows system tray.",
            }

        # 4. Telemetry kill_process check
        if pid == 0 or pid == 4 or pid == self.current_pid:
            return 400, {
                "error": "BadRequest",
                "message": f"PID {pid} is a protected system process and cannot be terminated.",
            }

        # 5. OS process existence check
        if pid not in self.active_processes:
            return 404, {
                "error": "NotFound",
                "message": f"Process with PID {pid} was not found.",
            }

        del self.active_processes[pid]
        return 200, {"success": True, "pid": pid}

    def execute_cloud_pending_command(self, cmd: Dict[str, Any]) -> Dict[str, Any]:
        """Simulates `cloud::execute_pending_command` for kill_process and merge_pr."""
        cmd_id = cmd.get("id", "unknown_id")
        cmd_type = cmd.get("type", "")
        payload = cmd.get("payload", {})

        if cmd_type == "kill_process":
            raw_pid = payload.get("pid")
            if raw_pid is None or not isinstance(raw_pid, int) or raw_pid < 0 or raw_pid > 4294967295:
                return {"id": cmd_id, "status": "failed", "result": "Missing 'pid' in payload"}

            pid = int(raw_pid)
            if pid == 0 or pid == 4 or pid == self.current_pid:
                return {
                    "id": cmd_id,
                    "status": "failed",
                    "result": "Protected system process cannot be terminated",
                }

            if not self.allow_delete_operations:
                return {
                    "id": cmd_id,
                    "status": "failed",
                    "result": "Operation disabled by desktop security policy",
                }

            if pid not in self.active_processes:
                return {
                    "id": cmd_id,
                    "status": "failed",
                    "result": f"Process with PID {pid} was not found.",
                }

            del self.active_processes[pid]
            return {
                "id": cmd_id,
                "status": "completed",
                "result": f"Process {pid} terminated",
            }

        if cmd_type == "merge_pr":
            if not self.allow_git_operations:
                return {
                    "id": cmd_id,
                    "status": "failed",
                    "result": "Operation disabled by desktop security policy",
                }

            repo_id = payload.get("repo_id")
            pr_number = payload.get("pr_number")
            if not repo_id:
                return {"id": cmd_id, "status": "failed", "result": "Missing 'repo_id' in payload"}
            if not pr_number or not isinstance(pr_number, int) or pr_number <= 0:
                return {"id": cmd_id, "status": "failed", "result": "Missing 'pr_number' in payload"}

            method = str(payload.get("method", "merge")).lower()
            if method not in ("merge", "squash", "rebase"):
                return {"id": cmd_id, "status": "failed", "result": f"Invalid merge method '{method}'"}

            return {
                "id": cmd_id,
                "status": "completed",
                "result": f"Successfully merged PR #{pr_number} via {method}",
            }

        return {"id": cmd_id, "status": "failed", "result": f"Unsupported command type '{cmd_type}'"}
