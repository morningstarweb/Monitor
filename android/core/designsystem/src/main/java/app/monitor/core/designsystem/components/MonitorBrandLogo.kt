package app.monitor.core.designsystem.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Mathematical Path Constants matching the official Monitor Brand Mark
private const val PATH_M_AND_TOP_BEZEL = "M 191 650 L 191 357 L 242 357 Q 257 357 265 369 L 316 447 L 378 349 Q 392 325 420 320 L 1162 196 Q 1239 183 1239 260 L 1239 454 A 118 118 0 0 0 1188 415 L 1188 286 Q 1188 244 1146 250 L 476 344 Q 438 350 438 386 L 438 613 L 383 613 L 383 451 L 316 555 L 247 450 L 247 614 Q 216 628 191 650 Z"
private const val PATH_BOTTOM_BEZEL_AND_STAND = "M 227 647 Q 247 635 268 627 L 268 666 Q 268 699 304 699 L 1152 699 Q 1188 699 1188 663 L 1188 630 A 118 118 0 0 0 1239 589 L 1239 684 Q 1239 742 1181 742 L 787 742 L 805 802 L 916 802 Q 945 802 945 831 L 945 835 L 505 835 L 505 831 Q 505 802 534 802 L 656 802 L 674 742 L 285 742 Q 227 742 227 684 Z"
private const val PATH_FIRST_O_OUTER = "M 548 423.5 A 101 101 0 1 0 548 625.5 A 101 101 0 1 0 548 423.5 Z"
private const val PATH_GIT_BRANCH_INSIDE_O = "M 522.75 484.35 A 19 19 0 1 1 545.22 479.01 L 578.00 509.05 A 19 19 0 1 1 567.53 520.47 L 538.25 493.58 L 538.25 546.65 A 19 19 0 1 1 522.75 546.65 Z"
private const val PATH_FIRST_O_COMPOUND = "$PATH_FIRST_O_OUTER $PATH_GIT_BRANCH_INSIDE_O"
private const val PATH_LETTER_N = "M 661 613 L 661 432 L 706 432 L 707 452 Q 730 426 768 426 Q 838 426 838 492 L 838 613 L 784 613 L 784 510 Q 784 478 748 478 Q 710 478 710 512 L 710 613 Z"
private const val PATH_LETTER_I_DOT = "M 886.5 363.5 A 28 28 0 1 0 886.5 419.5 A 28 28 0 1 0 886.5 363.5 Z"
private const val PATH_LETTER_I_STEM = "M 859 432 L 909 432 L 909 613 L 859 613 Z"
private const val PATH_LETTER_T = "M 931 380 L 979 380 L 979 432 L 1029 432 L 1029 478 L 979 478 L 979 552 Q 979 572 1002 572 L 1030 572 L 1030 614 L 990 614 Q 931 614 931 562 Z"
private const val PATH_SECOND_O = "M 1133.5 423.5 A 100.5 100.5 0 1 0 1133.5 624.5 A 100.5 100.5 0 1 0 1133.5 423.5 Z M 1132.5 475.8 A 48.2 48.2 0 1 1 1132.5 572.2 A 48.2 48.2 0 1 1 1132.5 475.8 Z"
private const val PATH_LETTER_R = "M 1249 614 L 1249 428 L 1301 428 L 1301 452 Q 1318 427 1348 427 Q 1358 427 1363 431 L 1363 479 Q 1353 476 1340 476 Q 1302 476 1302 512 L 1302 614 Z"

/**
 * High-Precision Vector "Monitor" Brand Wordmark & App Icon for Android Jetpack Compose.
 * Derived directly from the official Monitor geometry (100% pure paths, zero font dependencies).
 * Supports both Dark Mode (#0B1120) and Light Mode (#F8FAFC).
 */
@Composable
fun MonitorBrandWordmark(
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = true,
    monochrome: Boolean = false
) {
    val mAndTopBezel = remember {
        PathParser().parsePathString(PATH_M_AND_TOP_BEZEL).toPath()
    }
    val bottomBezelAndStand = remember {
        PathParser().parsePathString(PATH_BOTTOM_BEZEL_AND_STAND).toPath()
    }
    val firstOOuter = remember {
        PathParser().parsePathString(PATH_FIRST_O_OUTER).toPath()
    }
    val gitBranchInsideO = remember {
        PathParser().parsePathString(PATH_GIT_BRANCH_INSIDE_O).toPath()
    }
    val firstOCompound = remember {
        PathParser().parsePathString(PATH_FIRST_O_COMPOUND).toPath().apply {
            fillType = PathFillType.EvenOdd
        }
    }
    val letterN = remember { PathParser().parsePathString(PATH_LETTER_N).toPath() }
    val letterIDot = remember { PathParser().parsePathString(PATH_LETTER_I_DOT).toPath() }
    val letterIStem = remember { PathParser().parsePathString(PATH_LETTER_I_STEM).toPath() }
    val letterT = remember { PathParser().parsePathString(PATH_LETTER_T).toPath() }
    val secondO = remember {
        PathParser().parsePathString(PATH_SECOND_O).toPath().apply {
            fillType = PathFillType.EvenOdd
        }
    }
    val letterR = remember { PathParser().parsePathString(PATH_LETTER_R).toPath() }

    val transition = rememberInfiniteTransition(label = "monitorLogoPulse")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gitPulse"
    )

    val wordmarkColor = if (isDarkTheme) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val oCircleFill = if (isDarkTheme) Color(0xFF131C31) else Color(0xFF1E1B4B)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1250f / 700f)
            .then(modifier)
    ) {
        val scaleX = size.width / 1250f
        val scaleY = size.height / 700f
        val uniformScale = minOf(scaleX, scaleY)

        withTransform({
            scale(uniformScale, uniformScale, pivot = androidx.compose.ui.geometry.Offset.Zero)
            translate(left = -150f, top = -165f)
        }) {
            if (monochrome) {
                drawPath(mAndTopBezel, color = wordmarkColor)
                drawPath(bottomBezelAndStand, color = wordmarkColor)
                drawPath(firstOCompound, color = wordmarkColor)
                drawPath(letterN, color = wordmarkColor)
                drawPath(letterIDot, color = wordmarkColor)
                drawPath(letterIStem, color = wordmarkColor)
                drawPath(letterT, color = wordmarkColor)
                drawPath(secondO, color = wordmarkColor)
                drawPath(letterR, color = wordmarkColor)
            } else {
                val topBrush = Brush.horizontalGradient(
                    colors = listOf(Color(0xFF5B4DFF), Color(0xFF8B5CF6), Color(0xFF00D2FF))
                )
                val bottomBrush = Brush.horizontalGradient(
                    colors = listOf(Color(0xFF00D2FF), Color(0xFF5B4DFF), Color(0xFFA855F7))
                )
                val gitBrush = Brush.linearGradient(
                    colors = listOf(Color(0xFF10B981), Color(0xFF00D2FF))
                )
                drawPath(mAndTopBezel, brush = topBrush)
                drawPath(bottomBezelAndStand, brush = bottomBrush)
                drawPath(firstOOuter, color = oCircleFill)
                drawPath(gitBranchInsideO, brush = gitBrush, alpha = pulseAlpha)
                drawPath(letterN, color = wordmarkColor)
                drawPath(letterIDot, color = Color(0xFF00D2FF))
                drawPath(letterIStem, color = wordmarkColor)
                drawPath(letterT, color = wordmarkColor)
                drawPath(secondO, color = wordmarkColor)
                drawPath(letterR, color = wordmarkColor)
            }
        }
    }
}

/**
 * Compact Square App Badge / Hero Icon for OnboardingScreen & TopAppBar.
 */
@Composable
fun MonitorBrandBadgeIcon(
    sizeDp: Dp = 72.dp,
    modifier: Modifier = Modifier
) {
    val bezelPath = remember {
        PathParser().parsePathString(
            "M 88 330 L 88 164 L 116 164 Q 125 164 130 172 L 158 216 L 192 161 Q 200 148 216 145 L 402 114 Q 436 108 436 142 L 436 332 Q 436 364 404 364 L 296 364 L 306 400 L 354 400 Q 370 400 370 416 L 154 416 Q 154 400 170 400 L 218 400 L 228 364 L 120 364 Q 88 364 88 332 Z M 224 176 L 224 332 L 404 332 L 404 146 L 224 176 Z"
        ).toPath()
    }
    val gitPath = remember {
        PathParser().parsePathString(
            "M 297 217 A 13 13 0 1 1 312 213 L 334 233 A 13 13 0 1 1 327 241 L 307 223 L 307 259 A 13 13 0 1 1 297 259 Z"
        ).toPath()
    }
    val centerCircle = remember {
        PathParser().parsePathString(
            "M 314 176 A 68 68 0 1 0 314 312 A 68 68 0 1 0 314 176 Z"
        ).toPath()
    }
    Canvas(modifier = modifier.size(sizeDp)) {
        val s = size.minDimension / 512f
        scale(s, s, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            drawPath(
                path = bezelPath,
                brush = Brush.linearGradient(listOf(Color(0xFF5B4DFF), Color(0xFF8B5CF6), Color(0xFF00D2FF)))
            )
            drawPath(
                path = centerCircle,
                color = Color(0xFF131C31)
            )
            drawPath(
                path = gitPath,
                brush = Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF00D2FF)))
            )
        }
    }
}
