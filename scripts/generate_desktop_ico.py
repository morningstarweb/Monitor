import resvg_py
from PIL import Image
from pathlib import Path
import io
import os

PROJECT_ROOT = Path(__file__).resolve().parent.parent

svg_content = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1536 1536" width="1024" height="1024">
  <defs>
    <!-- Background: Deep Obsidian Black with rounded squircle corners -->
    <linearGradient id="bgGrad" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#0E1218"/>
      <stop offset="60%" stop-color="#060A08"/>
      <stop offset="100%" stop-color="#020304"/>
    </linearGradient>

    <!-- Bezel Top Linear Gradient: Neon Blue to Purple to Cyan -->
    <linearGradient id="monitorTopGrad" x1="191" y1="200" x2="1239" y2="650" gradientUnits="userSpaceOnUse">
      <stop offset="0%" stop-color="#5B4DFF"/>
      <stop offset="55%" stop-color="#8B5CF6"/>
      <stop offset="100%" stop-color="#00D2FF"/>
    </linearGradient>

    <!-- Bezel Bottom Linear Gradient: Cyan to Blue -->
    <linearGradient id="monitorBottomGrad" x1="227" y1="627" x2="1239" y2="835" gradientUnits="userSpaceOnUse">
      <stop offset="0%" stop-color="#00D2FF"/>
      <stop offset="100%" stop-color="#5B4DFF"/>
    </linearGradient>

    <!-- Git Branch Emerald to Cyan Gradient -->
    <linearGradient id="gitBranchGrad" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#10B981"/>
      <stop offset="100%" stop-color="#00E5FF"/>
    </linearGradient>
  </defs>

  <!-- Modern Windows 11 / Android Adaptive Squircle Icon Base -->
  <rect x="36" y="36" width="1464" height="1464" rx="340" fill="url(#bgGrad)" stroke="#1E293B" stroke-width="12"/>

  <!-- Centered Scaled Logo Group (exact translation and scale from ic_monitor_foreground) -->
  <g transform="translate(215, 385) scale(0.72)">
    <!-- 1. M + Top-Right Tilted Monitor Bezel -->
    <path d="M 191 650 L 191 357 L 242 357 Q 257 357 265 369 L 316 447 L 378 349 Q 392 325 420 320 L 1162 196 Q 1239 183 1239 260 L 1239 454 A 118 118 0 0 0 1188 415 L 1188 286 Q 1188 244 1146 250 L 476 344 Q 438 350 438 386 L 438 613 L 383 613 L 383 451 L 316 555 L 247 450 L 247 614 Q 216 628 191 650 Z" fill="url(#monitorTopGrad)"/>

    <!-- 2. Bottom Monitor Bezel + Stand Neck & Base -->
    <path d="M 227 647 Q 247 635 268 627 L 268 666 Q 268 699 304 699 L 1152 699 Q 1188 699 1188 663 L 1188 630 A 118 118 0 0 0 1239 589 L 1239 684 Q 1239 742 1181 742 L 787 742 L 805 802 L 916 802 Q 945 802 945 831 L 945 835 L 505 835 L 505 831 Q 505 802 534 802 L 656 802 L 674 742 L 285 742 Q 227 742 227 684 Z" fill="url(#monitorBottomGrad)"/>

    <!-- 3. First 'o' with EvenOdd Git Branch Knockout -->
    <path fill-rule="evenodd" fill="#F8FAFC" d="M 548 423.5 A 101 101 0 1 0 548 625.5 A 101 101 0 1 0 548 423.5 Z M 522.75 484.35 A 19 19 0 1 1 545.22 479.01 L 578.00 509.05 A 19 19 0 1 1 567.53 520.47 L 538.25 493.58 L 538.25 546.65 A 19 19 0 1 1 522.75 546.65 Z"/>

    <!-- 4. Glowing Emerald Git Branch Overlay -->
    <path fill="url(#gitBranchGrad)" d="M 522.75 484.35 A 19 19 0 1 1 545.22 479.01 L 578.00 509.05 A 19 19 0 1 1 567.53 520.47 L 538.25 493.58 L 538.25 546.65 A 19 19 0 1 1 522.75 546.65 Z"/>

    <!-- 5. Wordmark Letters: n, i, t, o, r -->
    <path fill="#F8FAFC" d="M 661 613 L 661 432 L 706 432 L 707 452 Q 730 426 768 426 Q 838 426 838 492 L 838 613 L 784 613 L 784 510 Q 784 478 748 478 Q 710 478 710 512 L 710 613 Z"/>
    <path fill="#00D2FF" d="M 886.5 363.5 A 28 28 0 1 0 886.5 419.5 A 28 28 0 1 0 886.5 363.5 Z"/>
    <path fill="#F8FAFC" d="M 859 432 L 909 432 L 909 613 L 859 613 Z"/>
    <path fill="#F8FAFC" d="M 931 380 L 979 380 L 979 432 L 1029 432 L 1029 478 L 979 478 L 979 552 Q 979 572 1002 572 L 1030 572 L 1030 614 L 990 614 Q 931 614 931 562 Z"/>
    <path fill-rule="evenodd" fill="#F8FAFC" d="M 1133.5 423.5 A 100.5 100.5 0 1 0 1133.5 624.5 A 100.5 100.5 0 1 0 1133.5 423.5 Z M 1132.5 475.8 A 48.2 48.2 0 1 1 1132.5 572.2 A 48.2 48.2 0 1 1 1132.5 475.8 Z"/>
    <path fill="#F8FAFC" d="M 1249 614 L 1249 428 L 1301 428 L 1301 452 Q 1318 427 1348 427 Q 1358 427 1363 431 L 1363 479 Q 1353 476 1340 476 Q 1302 476 1302 512 L 1302 614 Z"/>
  </g>
</svg>"""

print("Rendering high-res master 1024x1024 PNG with resvg...")
png_bytes = resvg_py.svg_to_bytes(svg_content)
master_img = Image.open(io.BytesIO(png_bytes)).convert("RGBA")

# Multi-resolution icon sizes for modern Windows 11/10
icon_sizes = [(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)]

targets = [
    str(PROJECT_ROOT / "desktop" / "crates" / "monitor-tray" / "src" / "monitor.ico"),
    str(PROJECT_ROOT / "installer" / "monitor.ico"),
]

for target in targets:
    master_img.save(target, format="ICO", sizes=icon_sizes)
    size_bytes = os.path.getsize(target)
    print(f"Saved Windows ICO: {target} ({size_bytes} bytes)")

# Also save high-res brand PNG in assets/brand/
brand_png = str(PROJECT_ROOT / "assets" / "brand" / "monitor-app-icon.png")
master_img.save(brand_png, format="PNG")
print(f"Saved Brand PNG: {brand_png}")
