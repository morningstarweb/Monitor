from __future__ import annotations

import concurrent.futures
import json
import math
import os
import re
import unittest
from pathlib import Path
from typing import Any, Dict, List, Optional

PROJECT_ROOT = Path(__file__).resolve().parents[2]
ANDROID_ROOT = PROJECT_ROOT / "android"

__all__ = [
    "concurrent", "json", "math", "os", "re", "unittest", "Path",
    "Any", "Dict", "List", "Optional", "PROJECT_ROOT", "ANDROID_ROOT",
]
