package com.nivukx.music.ui.motion

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Nivukx motion system.
 *
 * Motion is fast, coordinated and state-driven. Shared UI surfaces should animate
 * from one source of truth instead of stacking independent enter/exit animations.
 * Frame-critical work stays inside graphicsLayer/draw transforms.
 */
object NivukxMotion {
    const val Micro = 120
    const val Standard = 220
    const val Emphasis = 320
    const val Content = 240
    const val PlayerMorph = 280
    const val PremiumScreen = 300
    const val Navigation = 300
    const val MicroSettle = 180

    val StandardEasing = FastOutSlowInEasing
    val PremiumEasing = CubicBezierEasing(0.18f, 1f, 0.32f, 1f)
    val ExitEasing = CubicBezierEasing(0.4f, 0f, 1f, 1f)

    val StandardTween = tween<Float>(
        durationMillis = Standard,
        easing = StandardEasing,
    )

    val ContentTween = tween<Float>(
        durationMillis = Content,
        easing = StandardEasing,
    )

    val PlayerMorphTween = tween<Float>(
        durationMillis = PlayerMorph,
        easing = StandardEasing,
    )

    val SoftSpring = spring<Float>(
        dampingRatio = 0.82f,
        stiffness = Spring.StiffnessMediumLow,
    )

    val PlayerSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    val HighEndSpring = spring<Float>(
        dampingRatio = 0.88f,
        stiffness = 480f,
    )

    val NavigationEnterForward: EnterTransition =
        fadeIn(tween(Navigation, easing = PremiumEasing)) +
            slideInHorizontally(
                initialOffsetX = { it / 12 },
                animationSpec = tween(Navigation, easing = PremiumEasing),
            ) +
            scaleIn(
                initialScale = 0.985f,
                animationSpec = tween(Navigation, easing = PremiumEasing),
            )

    val NavigationExitForward: ExitTransition =
        fadeOut(tween(210, easing = ExitEasing)) +
            slideOutHorizontally(
                targetOffsetX = { -it / 18 },
                animationSpec = tween(210, easing = ExitEasing),
            ) +
            scaleOut(
                targetScale = 0.992f,
                animationSpec = tween(210, easing = ExitEasing),
            )

    val NavigationEnterBackward: EnterTransition =
        fadeIn(tween(Navigation, easing = PremiumEasing)) +
            slideInHorizontally(
                initialOffsetX = { -it / 12 },
                animationSpec = tween(Navigation, easing = PremiumEasing),
            ) +
            scaleIn(
                initialScale = 0.985f,
                animationSpec = tween(Navigation, easing = PremiumEasing),
            )

    val NavigationExitBackward: ExitTransition =
        fadeOut(tween(210, easing = ExitEasing)) +
            slideOutHorizontally(
                targetOffsetX = { it / 18 },
                animationSpec = tween(210, easing = ExitEasing),
            ) +
            scaleOut(
                targetScale = 0.992f,
                animationSpec = tween(210, easing = ExitEasing),
            )

    val Enter: EnterTransition =
        fadeIn(
            animationSpec = tween(Standard, easing = StandardEasing),
        ) +
            slideInVertically(
                initialOffsetY = { (it * 0.018f).toInt() },
                animationSpec = tween(Standard, easing = StandardEasing),
            ) +
            scaleIn(
                initialScale = 0.985f,
                animationSpec = tween(Standard, easing = StandardEasing),
            )

    val Exit: ExitTransition =
        fadeOut(
            animationSpec = tween(170, easing = StandardEasing),
        ) +
            scaleOut(
                targetScale = 0.99f,
                animationSpec = tween(170, easing = StandardEasing),
            )

    val SheetEnter: EnterTransition =
        fadeIn(
            animationSpec = tween(180, easing = StandardEasing),
        ) +
            slideInVertically(
                initialOffsetY = { (it * 0.035f).toInt() },
                animationSpec = tween(Standard, easing = StandardEasing),
            )

    val SheetExit: ExitTransition =
        fadeOut(
            animationSpec = tween(160, easing = StandardEasing),
        ) +
            slideOutVertically(
                targetOffsetY = { (it * 0.025f).toInt() },
                animationSpec = tween(180, easing = StandardEasing),
            )

    fun contentTransform(): ContentTransform =
        (
            fadeIn(
                animationSpec = tween(Content, easing = PremiumEasing),
            ) +
                slideInVertically(
                    initialOffsetY = { (it * 0.014f).toInt() },
                    animationSpec = tween(Content, easing = PremiumEasing),
                ) +
                scaleIn(
                    initialScale = 0.992f,
                    animationSpec = tween(Content, easing = PremiumEasing),
                )
            ).togetherWith(
                fadeOut(
                    animationSpec = tween(150, easing = ExitEasing),
                ) +
                    slideOutVertically(
                        targetOffsetY = { -(it * 0.008f).toInt() },
                        animationSpec = tween(150, easing = ExitEasing),
                    )
            )

    fun playerContentTransform(): ContentTransform =
        (
            fadeIn(
                animationSpec = tween(PlayerMorph, easing = PremiumEasing),
            ) +
                slideInVertically(
                    initialOffsetY = { (it * 0.012f).toInt() },
                    animationSpec = tween(PlayerMorph, easing = PremiumEasing),
                ) +
                scaleIn(
                    initialScale = 0.99f,
                    animationSpec = tween(PlayerMorph, easing = PremiumEasing),
                )
            ).togetherWith(
                fadeOut(
                    animationSpec = tween(160, easing = ExitEasing),
                ) +
                    slideOutVertically(
                        targetOffsetY = { -(it * 0.006f).toInt() },
                        animationSpec = tween(160, easing = ExitEasing),
                    )
            )
}

@Composable
fun EchoAnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = NivukxMotion.Enter,
        exit = NivukxMotion.Exit,
    ) {
        content()
    }
}

@Composable
fun <S> EchoAnimatedContent(
    targetState: S,
    modifier: Modifier = Modifier,
    label: String = "EchoAnimatedContent",
    playerTransition: Boolean = false,
    content: @Composable (S) -> Unit,
) {
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = {
            if (playerTransition) {
                NivukxMotion.playerContentTransform()
            } else {
                NivukxMotion.contentTransform()
            }
        },
        label = label,
    ) { state ->
        content(state)
    }
}

/**
 * Premium title identity motion. The title remains readable throughout the transition.
 */
@Composable
fun Modifier.echoTitleMotion(
    visible: Boolean,
    emphasis: Float = 1f,
): Modifier {
    val visibility = animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(
            durationMillis = NivukxMotion.Standard,
            easing = NivukxMotion.StandardEasing,
        ),
        label = "echoTitleVisibility",
    )
    val scale = animateFloatAsState(
        targetValue = if (visible) 1f else 0.965f,
        animationSpec = NivukxMotion.SoftSpring,
        label = "echoTitleScale",
    )

    return graphicsLayer {
        val progress = visibility.value
        alpha = progress
        translationX = (1f - progress) * -12f * emphasis
        scaleX = scale.value
        scaleY = scale.value
    }
}

/**
 * Lightweight press treatment. Uses graphicsLayer so it does not trigger layout work.
 */
@Composable
fun Modifier.echoPress(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.965f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = NivukxMotion.SoftSpring,
        label = "echoPressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
