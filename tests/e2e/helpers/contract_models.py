"""Contract models and data structures for Monitor E2E testing.

Matches interface contracts specified in PROJECT.md and API_REFERENCE.md.
"""

from __future__ import annotations

import dataclasses
from typing import Any, Dict, List, Optional


@dataclasses.dataclass
class CpuSnapshot:
    total_usage_percent: float
    core_count: int

    def to_dict(self) -> Dict[str, Any]:
        return {
            "totalUsagePercent": self.total_usage_percent,
            "coreCount": self.core_count,
        }


@dataclasses.dataclass
class MemorySnapshot:
    used_bytes: int
    total_bytes: int

    def to_dict(self) -> Dict[str, Any]:
        return {
            "usedBytes": self.used_bytes,
            "totalBytes": self.total_bytes,
        }


@dataclasses.dataclass
class GpuSnapshot:
    usage_percent: float
    temperature_celsius: Optional[float] = None

    def to_dict(self) -> Dict[str, Any]:
        return {
            "usagePercent": self.usage_percent,
            "temperatureCelsius": self.temperature_celsius,
        }


@dataclasses.dataclass
class BatterySnapshot:
    level_percent: int
    is_charging: bool
    remaining_seconds: Optional[int] = None

    def to_dict(self) -> Dict[str, Any]:
        return {
            "levelPercent": self.level_percent,
            "isCharging": self.is_charging,
            "remainingSeconds": self.remaining_seconds,
        }


@dataclasses.dataclass
class DiskSnapshot:
    name: str
    mount_point: str
    total_bytes: int
    available_bytes: int

    def to_dict(self) -> Dict[str, Any]:
        return {
            "name": self.name,
            "mount_point": self.mount_point,
            "total_bytes": self.total_bytes,
            "available_bytes": self.available_bytes,
        }


@dataclasses.dataclass
class ProcessSnapshot:
    pid: int
    name: str
    cpu_percent: float
    memory_mb: float

    def to_dict(self) -> Dict[str, Any]:
        return {
            "pid": self.pid,
            "name": self.name,
            "cpuPercent": self.cpu_percent,
            "memoryMb": self.memory_mb,
        }


@dataclasses.dataclass
class TelemetrySnapshot:
    observed_at_utc_ms: int
    cpu: CpuSnapshot
    memory: MemorySnapshot
    gpu: Optional[GpuSnapshot] = None
    battery: Optional[BatterySnapshot] = None
    disks: List[DiskSnapshot] = dataclasses.field(default_factory=list)
    cooling: Optional[Dict[str, Any]] = None  # None or physical sensor telemetry

    def to_dict(self) -> Dict[str, Any]:
        result: Dict[str, Any] = {
            "observedAtUtcMs": self.observed_at_utc_ms,
            "cpu": self.cpu.to_dict(),
            "memory": self.memory.to_dict(),
            "disks": [d.to_dict() for d in self.disks],
            "cooling": self.cooling,
        }
        if self.gpu:
            result["gpu"] = self.gpu.to_dict()
        if self.battery:
            result["battery"] = self.battery.to_dict()
        return result


@dataclasses.dataclass
class PullRequestInfo:
    number: int
    title: str
    author: str
    state: str
    base_branch: str
    head_branch: str
    updated_at: str
    url: str

    def to_dict(self) -> Dict[str, Any]:
        return {
            "number": self.number,
            "title": self.title,
            "author": self.author,
            "state": self.state,
            "base_branch": self.base_branch,
            "head_branch": self.head_branch,
            "updated_at": self.updated_at,
            "url": self.url,
        }


@dataclasses.dataclass
class GitProject:
    repo_id: str
    display_name: str
    path: str
    active_branch: str
    is_dirty: bool
    uncommitted_count: int

    def to_dict(self) -> Dict[str, Any]:
        return {
            "repoId": self.repo_id,
            "displayName": self.display_name,
            "path": self.path,
            "activeBranch": self.active_branch,
            "isDirty": self.is_dirty,
            "uncommittedCount": self.uncommitted_count,
        }


@dataclasses.dataclass
class SettingsState:
    broker_url: str = "https://broker.monitor.internal"
    sampling_rate_seconds: int = 2
    temp_unit: str = "celsius"  # "celsius" or "fahrenheit"
    cpu_alert_threshold: int = 85
    ram_alert_threshold: int = 90
    battery_alert_threshold: int = 20
    biometric_enabled: bool = False
    background_sync_enabled: bool = True

    def to_dict(self) -> Dict[str, Any]:
        return {
            "brokerUrl": self.broker_url,
            "samplingRateSeconds": self.sampling_rate_seconds,
            "tempUnit": self.temp_unit,
            "cpuAlertThreshold": self.cpu_alert_threshold,
            "ramAlertThreshold": self.ram_alert_threshold,
            "batteryAlertThreshold": self.battery_alert_threshold,
            "biometricEnabled": self.biometric_enabled,
            "backgroundSyncEnabled": self.background_sync_enabled,
        }
